package com.myano.skoruba4j.protocol;

import java.time.Instant;
import java.util.Base64;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Issues refresh tokens for IS4 {@code AllowOfflineAccess} clients, including public clients
 * ({@code RequireClientSecret=false}).
 *
 * <p>SAS {@code OAuth2RefreshTokenGenerator} returns null for authorization_code when client
 * authentication is {@code none}, so admin.web (PKCE public client, 300s access tokens) never gets
 * a refresh_token and AccessTokenManagement falls into a sign-out / reload loop.
 */
public final class Is4RefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {
  private final StringKeyGenerator refreshTokenGenerator =
      new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

  @Override
  public OAuth2RefreshToken generate(OAuth2TokenContext context) {
    if (context == null || !OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
      return null;
    }
    RegisteredClient client = context.getRegisteredClient();
    if (client == null
        || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
      return null;
    }
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(client.getTokenSettings().getRefreshTokenTimeToLive());
    return new OAuth2RefreshToken(refreshTokenGenerator.generateKey(), issuedAt, expiresAt);
  }
}
