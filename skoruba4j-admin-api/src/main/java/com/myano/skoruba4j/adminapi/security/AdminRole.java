package com.myano.skoruba4j.adminapi.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/** Admin role must exist on the token. Never treat every authenticated user as admin. */
public final class AdminRole {
  private AdminRole() {}

  public static boolean hasRole(Collection<? extends GrantedAuthority> authorities, String role) {
    if (role == null || role.isBlank() || authorities == null) {
      return false;
    }
    String required = role.trim();
    for (GrantedAuthority authority : authorities) {
      if (authority != null && required.equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }
}
