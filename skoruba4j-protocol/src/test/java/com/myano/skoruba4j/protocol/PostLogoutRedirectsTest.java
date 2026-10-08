package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class PostLogoutRedirectsTest {

  @Test
  void acceptsAspNetSignOutCallbackOnSameOriginAsRedirectUri() {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("MyClientId")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:6061/signin-oidc")
            .build();
    assertTrue(
        PostLogoutRedirects.allowed(client, "https://localhost:6061/signout-callback-oidc"));
    assertFalse(PostLogoutRedirects.allowed(client, "https://evil.example/signout-callback-oidc"));
    assertFalse(PostLogoutRedirects.allowed(client, "https://localhost:6061/other"));
  }
}
