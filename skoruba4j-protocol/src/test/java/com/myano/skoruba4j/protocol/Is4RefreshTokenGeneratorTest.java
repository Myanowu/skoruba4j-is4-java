package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;

class Is4RefreshTokenGeneratorTest {

  private final Is4RefreshTokenGenerator generator = new Is4RefreshTokenGenerator();

  @Test
  void issuesRefreshTokenForPublicClientWithOfflineAccess() {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("4sAdminWeb")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://localhost:8000/signin-oidc")
            .scope("openid")
            .scope("offline_access")
            .tokenSettings(
                TokenSettings.builder().refreshTokenTimeToLive(Duration.ofDays(30)).build())
            .build();
    OAuth2TokenContext context =
        DefaultOAuth2TokenContext.builder()
            .registeredClient(client)
            .tokenType(OAuth2TokenType.REFRESH_TOKEN)
            .authorizedScopes(client.getScopes())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();
    OAuth2RefreshToken token = generator.generate(context);
    assertNotNull(token);
    assertNotNull(token.getTokenValue());
    assertNotNull(token.getExpiresAt());
    assertNotNull(token.getIssuedAt());
  }

  @Test
  void skipsWhenClientHasNoRefreshGrant() {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("no-refresh")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:8000/cb")
            .scope("openid")
            .build();
    OAuth2TokenContext context =
        DefaultOAuth2TokenContext.builder()
            .registeredClient(client)
            .tokenType(OAuth2TokenType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();
    assertNull(generator.generate(context));
  }

  @Test
  void skipsNonRefreshTokenType() {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("x")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://localhost:8000/cb")
            .scope("openid")
            .build();
    OAuth2TokenContext context =
        DefaultOAuth2TokenContext.builder()
            .registeredClient(client)
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();
    assertNull(generator.generate(context));
  }
}
