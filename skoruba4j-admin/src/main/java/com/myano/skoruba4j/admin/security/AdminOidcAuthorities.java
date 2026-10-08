package com.myano.skoruba4j.admin.security;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Maps OIDC claims and Identity UserRoles (read-only) onto Spring authorities. */
public final class AdminOidcAuthorities {
  private AdminOidcAuthorities() {}

  public static List<GrantedAuthority> merge(
      Collection<? extends GrantedAuthority> existing,
      Object roleClaim,
      Object rolesClaim,
      String subject,
      Optional<JdbcRepositories> jdbc) {
    Set<String> names = new LinkedHashSet<>();
    if (existing != null) {
      for (GrantedAuthority authority : existing) {
        if (authority != null && authority.getAuthority() != null && !authority.getAuthority().isBlank()) {
          String name = AdminRole.normalize(authority.getAuthority());
          if (!name.isEmpty()) {
            names.add(name);
          }
        }
      }
    }
    addClaimValues(names, roleClaim);
    addClaimValues(names, rolesClaim);
    if (jdbc != null && jdbc.isPresent() && subject != null && !subject.isBlank()) {
      for (String name : jdbc.get().users().listRoleNames(subject)) {
        String role = AdminRole.normalize(name);
        if (!role.isEmpty()) {
          names.add(role);
        }
      }
    }
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String name : names) {
      authorities.add(new SimpleGrantedAuthority(name));
    }
    return authorities;
  }

  static void addClaimValues(Set<String> names, Object claim) {
    if (claim instanceof Collection<?> values) {
      for (Object value : values) {
        if (value != null && !value.toString().isBlank()) {
          String name = AdminRole.normalize(value.toString());
          if (!name.isEmpty()) {
            names.add(name);
          }
        }
      }
    } else if (claim instanceof String value && !value.isBlank()) {
      String name = AdminRole.normalize(value);
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
  }
}
