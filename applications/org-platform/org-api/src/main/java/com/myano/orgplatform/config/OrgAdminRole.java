package com.myano.orgplatform.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * STS admin role check. The role must already be on the signed-in user. This app never grants it.
 */
public final class OrgAdminRole {
  private OrgAdminRole() {}

  /** True when {@code role} is present, ignoring a {@code ROLE_} prefix and letter case. */
  public static boolean hasRole(Collection<? extends GrantedAuthority> authorities, String role) {
    String required = normalize(role);
    if (required.isEmpty() || authorities == null) {
      return false;
    }
    for (GrantedAuthority authority : authorities) {
      if (authority != null && required.equalsIgnoreCase(normalize(authority.getAuthority()))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Copies STS {@code role} / {@code roles} claims onto the OIDC user. Identity stores the name
   * without {@code ROLE_}; Spring may add that prefix.
   */
  public static List<GrantedAuthority> merge(
      Collection<? extends GrantedAuthority> existing, Object roleClaim, Object rolesClaim) {
    Set<String> names = new LinkedHashSet<>();
    if (existing != null) {
      for (GrantedAuthority authority : existing) {
        if (authority != null && authority.getAuthority() != null) {
          String name = normalize(authority.getAuthority());
          if (!name.isEmpty() && !name.regionMatches(true, 0, "SCOPE_", 0, 6)) {
            names.add(name);
          }
        }
      }
    }
    addClaimValues(names, roleClaim);
    addClaimValues(names, rolesClaim);
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String name : names) {
      authorities.add(new SimpleGrantedAuthority(name));
    }
    return authorities;
  }

  /** Strips a leading {@code ROLE_} so {@code MyRole} and {@code ROLE_MyRole} compare equal. */
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

  private static void addClaimValues(Set<String> names, Object claim) {
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
}
