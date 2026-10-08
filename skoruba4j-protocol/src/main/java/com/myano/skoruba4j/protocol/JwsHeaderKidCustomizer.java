package com.myano.skoruba4j.protocol;

import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

/** Puts {@code kid} on JWS headers so IdentityModel can select the JWKS key. */
public final class JwsHeaderKidCustomizer {
  private JwsHeaderKidCustomizer() {}

  public static void apply(JwtEncodingContext context, String keyId) {
    if (context == null || keyId == null || keyId.isBlank() || context.getJwsHeader() == null) {
      return;
    }
    context.getJwsHeader().keyId(keyId);
  }
}
