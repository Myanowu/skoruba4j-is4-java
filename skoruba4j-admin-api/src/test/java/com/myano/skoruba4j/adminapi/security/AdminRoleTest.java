package com.myano.skoruba4j.adminapi.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminRoleTest {

  @Test
  void neverGrantsAdminToUnauthenticatedOrUnrelatedRoles() {
    assertFalse(AdminRole.hasRole(List.of(), "MyRole"));
    assertFalse(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("User")), "MyRole"));
    assertFalse(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("MyRole")), ""));
    assertTrue(AdminRole.hasRole(List.of(new SimpleGrantedAuthority("MyRole")), "MyRole"));
  }

  @Test
  void clientCredentialsTokenRoleIsAnAuthority() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
            .claim("client_role", List.of("Admin", "MyRole"))
            .build();
    assertTrue(
        AdminApiJwtAuthorities.from(jwt).stream().anyMatch(a -> "MyRole".equals(a.getAuthority())));
    assertTrue(ApiRoleSuccessHandler.hasRole(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "machine", "n/a", AdminApiJwtAuthorities.from(jwt)),
        "MyRole"));
  }
}
