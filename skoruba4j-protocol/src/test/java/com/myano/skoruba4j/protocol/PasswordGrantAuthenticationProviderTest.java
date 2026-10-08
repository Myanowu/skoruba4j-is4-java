package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

class PasswordGrantAuthenticationProviderTest {

  private PasswordGrantAuthenticationProvider provider;
  private AtomicReference<AuthenticationManager> authManager;

  @BeforeEach
  void setUp() {
    authManager = new AtomicReference<>();
    AuthenticationManager manager =
        authentication -> {
          AuthenticationManager delegate = authManager.get();
          if (delegate == null) {
            throw new IllegalStateException("auth manager not set");
          }
          return delegate.authenticate(authentication);
        };
    OAuth2TokenGenerator<OAuth2Token> tokenGenerator =
        context -> {
          Instant now = Instant.now();
          // Mirror JwtGenerator: ACCESS_TOKEN is a Jwt, not OAuth2AccessToken.
          if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return org.springframework.security.oauth2.jwt.Jwt.withTokenValue("access-token-value")
                .header("alg", "none")
                .claim("sub", context.getPrincipal().getName())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
          }
          if ("id_token".equals(context.getTokenType().getValue())) {
            return org.springframework.security.oauth2.jwt.Jwt.withTokenValue("id-token-value")
                .header("alg", "none")
                .claim("sub", context.getPrincipal().getName())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
          }
          return null;
        };
    provider =
        new PasswordGrantAuthenticationProvider(
            manager, new InMemoryOAuth2AuthorizationService(), tokenGenerator);
    AuthorizationServerContextHolder.setContext(
        new AuthorizationServerContext() {
          @Override
          public String getIssuer() {
            return "https://localhost:5051";
          }

          @Override
          public AuthorizationServerSettings getAuthorizationServerSettings() {
            return AuthorizationServerSettings.builder().issuer("https://localhost:5051").build();
          }
        });
  }

  @AfterEach
  void clear() {
    AuthorizationServerContextHolder.resetContext();
  }

  @Test
  void rejectsClientWithoutPasswordGrant() {
    authManager.set(authentication -> authentication);
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("web")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://app/cb")
            .scope("openid")
            .build();
    PasswordGrantAuthenticationToken grant =
        new PasswordGrantAuthenticationToken(clientAuth(client), "demo", "x", Map.of());
    OAuth2AuthenticationException ex =
        assertThrows(OAuth2AuthenticationException.class, () -> provider.authenticate(grant));
    assertEquals(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, ex.getError().getErrorCode());
  }

  @Test
  void rejectsBadPassword() {
    authManager.set(
        authentication -> {
          throw new BadCredentialsException("bad");
        });
    PasswordGrantAuthenticationToken grant =
        new PasswordGrantAuthenticationToken(
            clientAuth(passwordClient()), "demo", "wrong", Map.of());
    OAuth2AuthenticationException ex =
        assertThrows(OAuth2AuthenticationException.class, () -> provider.authenticate(grant));
    assertEquals(OAuth2ErrorCodes.INVALID_GRANT, ex.getError().getErrorCode());
  }

  @Test
  void issuesAccessTokenOnSuccess() {
    authManager.set(
        authentication ->
            UsernamePasswordAuthenticationToken.authenticated(
                "user-1", "n/a", AuthorityUtils.createAuthorityList("MyRole")));
    PasswordGrantAuthenticationToken grant =
        new PasswordGrantAuthenticationToken(
            clientAuth(passwordClient()),
            "demo",
            "Passw0rd!",
            Map.of(OAuth2ParameterNames.SCOPE, "openid profile"));
    Authentication result = provider.authenticate(grant);
    assertTrue(result instanceof OAuth2AccessTokenAuthenticationToken);
    assertEquals(
        "access-token-value",
        ((OAuth2AccessTokenAuthenticationToken) result).getAccessToken().getTokenValue());
  }

  private static OAuth2ClientAuthenticationToken clientAuth(RegisteredClient client) {
    OAuth2ClientAuthenticationToken clientAuth =
        new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
    clientAuth.setAuthenticated(true);
    return clientAuth;
  }

  private static RegisteredClient passwordClient() {
    return RegisteredClient.withId("1")
        .clientId("skoruba4j-admin")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.PASSWORD)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://localhost:6061/signin-oidc")
        .scope("openid")
        .scope("profile")
        .build();
  }
}
