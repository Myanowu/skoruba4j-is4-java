package com.myano.skoruba4j.sts.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class StsFormLogoutTest {

  @Test
  void allowedSignOutCallbackReturnsToThatAdmin() {
    assertEquals(
        "https://localhost:6061/signout-callback-oidc",
        StsFormLogout.target(
            clients(), "MyClientId", "https://localhost:6061/signout-callback-oidc"));
  }

  @Test
  void unknownRedirectStaysOnStsLogin() {
    assertEquals(
        "/login?logout",
        StsFormLogout.target(clients(), "MyClientId", "https://evil.example/signout-callback-oidc"));
    assertEquals("/login?logout", StsFormLogout.target(clients(), null, null));
  }

  private static InMemoryRegisteredClientRepository clients() {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("MyClientId")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:6061/signin-oidc")
            .build();
    return new InMemoryRegisteredClientRepository(client);
  }
}
