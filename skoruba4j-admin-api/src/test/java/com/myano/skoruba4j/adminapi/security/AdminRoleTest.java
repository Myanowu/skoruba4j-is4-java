package com.myano.skoruba4j.adminapi.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminRoleTest {

  @Test
  void neverGrantsAdminToUnauthenticatedOrUnrelatedRoles() {
    assertFalse(AdminRole.hasRole(List.of(), "MyRole"));
    assertFalse(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("User")), "MyRole"));
    assertFalse(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("MyRole")), ""));
    assertTrue(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("MyRole")), "MyRole"));
  }
}
