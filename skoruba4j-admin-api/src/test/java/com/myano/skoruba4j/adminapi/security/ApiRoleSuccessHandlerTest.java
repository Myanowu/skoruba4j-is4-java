package com.myano.skoruba4j.adminapi.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ApiRoleSuccessHandlerTest {

  @Test
  void rejectsUserWithoutAdminRole() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "user", "x", List.of(new SimpleGrantedAuthority("User")));
    assertFalse(ApiRoleSuccessHandler.hasRole(auth, "MyRole"));
    assertFalse(AdminApiAccess.hasAdminRole(auth, "MyRole"));
  }

  @Test
  void acceptsAdminRoleCaseInsensitive() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "admin", "x", List.of(new SimpleGrantedAuthority("myrole")));
    assertTrue(ApiRoleSuccessHandler.hasRole(auth, "MyRole"));
    assertTrue(AdminApiAccess.hasAdminRole(auth, "MyRole"));
  }

  @Test
  void acceptsRolePrefixedAuthority() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "admin", "x", List.of(new SimpleGrantedAuthority("ROLE_MyRole")));
    assertTrue(ApiRoleSuccessHandler.hasRole(auth, "MyRole"));
  }
}
