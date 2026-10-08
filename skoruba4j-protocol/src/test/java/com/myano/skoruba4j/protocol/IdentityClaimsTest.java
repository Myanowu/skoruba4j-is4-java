package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityClaimsTest {

  @Test
  void subIsUserIdEvenWithoutUserRow() {
    Map<String, Object> claims = IdentityClaims.claims("user-guid", null, Set.of("openid"));
    assertEquals("user-guid", claims.get("sub"));
    assertFalse(claims.containsKey("email"));
  }

  @Test
  void profileAndEmailFollowScopes() {
    IdentityUser user =
        new IdentityUser(
            "user-guid",
            "alice",
            "ALICE",
            "alice@example.com",
            "ALICE@EXAMPLE.COM",
            true,
            "hash",
            "stamp",
            false,
            null,
            0,
            false,
            null,
            false);
    Map<String, Object> claims =
        IdentityClaims.claims("user-guid", user, Set.of("openid", "profile", "email"));
    assertEquals("alice", claims.get("preferred_username"));
    assertEquals("alice", claims.get("name"));
    assertEquals("alice@example.com", claims.get("email"));
    assertEquals(true, claims.get("email_verified"));
  }

  @Test
  void roleNamesFromAuthorities() {
    var roles =
        IdentityClaims.roleNames(
            java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("MyRole")));
    assertEquals(java.util.List.of("MyRole"), roles);
  }
}
