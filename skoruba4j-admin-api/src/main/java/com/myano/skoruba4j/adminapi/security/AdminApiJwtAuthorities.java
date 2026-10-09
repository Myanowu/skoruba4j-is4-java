package com.myano.skoruba4j.adminapi.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authorities for Admin API JWTs. User tokens carry {@code role}. IdentityServer4 client-credentials
 * tokens carry the same values as {@code client_role} ({@code ClientClaimsPrefix} + {@code role}).
 */
public final class AdminApiJwtAuthorities {
  private AdminApiJwtAuthorities() {}

  public static Collection<GrantedAuthority> from(Jwt jwt) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    if (jwt != null) {
      collect(names, jwt.getClaim("role"));
      collect(names, jwt.getClaim("client_role"));
    }
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String name : names) {
      authorities.add(new SimpleGrantedAuthority(name));
    }
    return authorities;
  }

  private static void collect(LinkedHashSet<String> names, Object claim) {
    if (claim instanceof Collection<?> values) {
      for (Object value : values) {
        add(names, value);
      }
      return;
    }
    add(names, claim);
  }

  private static void add(LinkedHashSet<String> names, Object value) {
    if (value == null) {
      return;
    }
    String text = value.toString().trim();
    if (!text.isEmpty()) {
      names.add(text);
    }
  }
}
