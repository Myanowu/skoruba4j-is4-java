package com.myano.orgplatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.orgplatform.config.OrgAdminRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OrgAdminRoleTest {

  @Test
  void roleClaimMatchesWithOrWithoutPrefix() {
    assertFalse(OrgAdminRole.hasRole(List.of(new SimpleGrantedAuthority("User")), "MyRole"));
    assertTrue(OrgAdminRole.hasRole(List.of(new SimpleGrantedAuthority("MyRole")), "MyRole"));
    assertTrue(OrgAdminRole.hasRole(List.of(new SimpleGrantedAuthority("ROLE_MyRole")), "MyRole"));
    assertEquals("MyRole", OrgAdminRole.normalize("ROLE_MyRole"));
  }

  @Test
  void mergesRoleClaimArrays() {
    var authorities = OrgAdminRole.merge(List.of(), List.of("MyRole", "User"), null);
    assertTrue(OrgAdminRole.hasRole(authorities, "MyRole"));
    assertTrue(OrgAdminRole.hasRole(authorities, "User"));
    assertFalse(OrgAdminRole.hasRole(authorities, "Admin"));
  }
}
