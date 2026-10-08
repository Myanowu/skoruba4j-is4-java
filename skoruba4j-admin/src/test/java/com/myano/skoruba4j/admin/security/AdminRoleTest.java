package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminRoleTest {

  @Test
  void doesNotTreatEveryUserAsAdmin() {
    assertFalse(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("User")), "MyRole"));
    assertTrue(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("MyRole")), "MyRole"));
    assertTrue(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("ROLE_MyRole")), "MyRole"));
  }
}
