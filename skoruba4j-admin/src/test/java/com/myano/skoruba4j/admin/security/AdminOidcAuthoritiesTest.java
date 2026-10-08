package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminOidcAuthoritiesTest {

  @Test
  void mapsRolesClaimWithoutGrantingEveryoneAdmin() {
    var authorities =
        AdminOidcAuthorities.merge(
            List.of(new SimpleGrantedAuthority("OIDC_USER")),
            null,
            List.of("Admin"),
            "user-id",
            Optional.empty());
    assertTrue(authorities.stream().anyMatch(a -> "Admin".equals(a.getAuthority())));
    var onlyOidc =
        AdminOidcAuthorities.merge(
            List.of(new SimpleGrantedAuthority("OIDC_USER")),
            null,
            null,
            "user-id",
            Optional.empty());
    assertTrue(onlyOidc.stream().noneMatch(a -> "Admin".equals(a.getAuthority())));
  }
}
