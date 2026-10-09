package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.configstore.ClientConfiguration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class RegisteredClientMapperTest {

  @Test
  void mapsPasswordGrant() {
    ClientConfiguration config =
        ClientConfiguration.protocolSample(
            1,
            "skoruba4j-admin",
            "Admin",
            true,
            false,
            true,
            false,
            3600,
            86400,
            List.of("authorization_code", "password"),
            List.of("openid", "profile"),
            List.of("https://localhost:6061/signin-oidc"),
            List.of("https://localhost:6061/signout-callback-oidc"),
            List.of());
    RegisteredClient client =
        RegisteredClientMapper.toRegisteredClient(config, Instant.parse("2026-01-01T00:00:00Z"));
    assertTrue(client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.PASSWORD));
    assertTrue(client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE));
  }

  @Test
  void mapsRefreshOfflineAccessPostLogoutAndDelegation() {
    ClientConfiguration config =
        ClientConfiguration.protocolSample(
            1,
            "demo-web",
            "Demo",
            true,
            true,
            true,
            true,
            3600,
            86400,
            List.of("authorization_code", "delegation"),
            List.of("api1"),
            List.of("https://app.example/callback"),
            List.of("https://app.example/signed-out"),
            List.of(new ClientConfiguration.ClientSecretValue(0, "secret", "SharedSecret", null)));
    RegisteredClient client = RegisteredClientMapper.toRegisteredClient(config, Instant.parse("2026-01-01T00:00:00Z"));
    assertTrue(client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE));
    assertTrue(client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN));
    assertTrue(client.getAuthorizationGrantTypes().contains(DelegationGrantAuthenticationToken.DELEGATION));
    assertTrue(client.getScopes().contains("openid"));
    assertTrue(client.getScopes().contains("offline_access"));
    assertTrue(client.getScopes().contains("api1"));
    assertTrue(client.getPostLogoutRedirectUris().contains("https://app.example/signed-out"));
  }

  @Test
  void strictModeDropsRefreshSoExpiredAccessTokenRequiresLogin() {
    ClientConfiguration config =
        ClientConfiguration.protocolSample(
            1,
            "demo-web",
            "Demo",
            true,
            true,
            true,
            true,
            3600,
            86400,
            List.of("authorization_code", "refresh_token"),
            List.of("api1"),
            List.of("https://app.example/callback"),
            List.of("https://app.example/signed-out"),
            List.of(new ClientConfiguration.ClientSecretValue(0, "secret", "SharedSecret", null)));
    RegisteredClient client =
        RegisteredClientMapper.toRegisteredClient(
            config, Instant.parse("2026-01-01T00:00:00Z"), EndSessionMode.STRICT);
    assertTrue(client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE));
    assertFalse(client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN));
    assertFalse(client.getScopes().contains("offline_access"));
  }

  @Test
  void keepsEverySharedSecretSoARotatedSecretStillAuthenticates() {
    ClientConfiguration config =
        ClientConfiguration.protocolSample(
            5,
            "machine",
            "Machine",
            true,
            true,
            false,
            false,
            3600,
            86400,
            List.of("client_credentials"),
            List.of("api"),
            List.of(),
            List.of(),
            List.of(
                new ClientConfiguration.ClientSecretValue(
                    1, Is4ClientSecretPasswordEncoder.sha256Base64("old"), "SharedSecret", null),
                new ClientConfiguration.ClientSecretValue(
                    2,
                    Is4ClientSecretPasswordEncoder.sha256Base64("current"),
                    "SharedSecret",
                    null)));
    RegisteredClient client =
        RegisteredClientMapper.toRegisteredClient(config, Instant.parse("2026-01-01T00:00:00Z"));
    Is4ClientSecretPasswordEncoder encoder = new Is4ClientSecretPasswordEncoder();
    assertTrue(encoder.matches("old", client.getClientSecret()));
    assertTrue(encoder.matches("current", client.getClientSecret()));
    assertFalse(encoder.matches("other", client.getClientSecret()));
  }
}
