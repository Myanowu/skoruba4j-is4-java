package com.myano.skoruba4j.adminapi.security;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Maps OIDC role claims + Identity UserRoles onto Spring authorities. */
public final class UiOidcAuthorities {
  private UiOidcAuthorities() {}

  public static List<GrantedAuthority> merge(
      Collection<? extends GrantedAuthority> existing,
      Object roleClaim,
      Object rolesClaim,
      String subject,
      Optional<JdbcRepositories> jdbc) {
    Set<String> names = new LinkedHashSet<>();
    if (existing != null) {
      for (GrantedAuthority authority : existing) {
        if (authority != null
            && authority.getAuthority() != null
            && !authority.getAuthority().isBlank()) {
          String name = normalize(authority.getAuthority());
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
        String role = normalize(name);
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
          String name = normalize(value.toString());
          if (!name.isEmpty()) {
            names.add(name);
          }
        }
      }
    } else if (claim instanceof String value && !value.isBlank()) {
      String name = normalize(value);
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
  }

  public static String normalize(String role) {
    if (role == null) {
      return "";
    }
    String value = role.trim();
    if (value.regionMatches(true, 0, "ROLE_", 0, 5)) {
      value = value.substring(5).trim();
    }
    return value;
  }
}
