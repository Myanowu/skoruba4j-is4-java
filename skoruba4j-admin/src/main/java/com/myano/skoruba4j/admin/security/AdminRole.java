package com.myano.skoruba4j.admin.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/** Admin role must exist on the OIDC principal. Never treat every signed-in user as admin. */
public final class AdminRole {
  private AdminRole() {}

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
   * Identity role names are stored without {@code ROLE_}. Spring {@code User.roles()} may add that
   * prefix; Admin checks must treat both forms as the same role.
   */
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
