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

  @Test
  void doesNotTreatSpaOriginAsAspNetSignOutHost() {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("4sAdminWeb")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:44300/signin-oidc")
            .redirectUri("https://localhost:8000/")
            .build();
    assertTrue(
        PostLogoutRedirects.allowed(client, "https://localhost:44300/signout-callback-oidc"));
    assertFalse(
        PostLogoutRedirects.allowed(client, "https://localhost:8000/signout-callback-oidc"));
  }

  @Test
  void doesNotInventAspNetSignOutOnHybridSpaHostWithSignInOidc() {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("4sAdminWeb")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:8000/signin-oidc")
            .redirectUri("https://localhost:8000/login-callback/")
            .redirectUri("https://localhost:8000/silent-callback/")
            .build();
    assertFalse(
        PostLogoutRedirects.allowed(client, "https://localhost:8000/signout-callback-oidc"));
  }

  @Test
  void stillAllowsExplicitlyRegisteredSignOutOnHybridSpaHost() {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("4sAdminWeb")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:8000/signin-oidc")
            .redirectUri("https://localhost:8000/login-callback/")
            .postLogoutRedirectUri("https://localhost:8000/signout-callback-oidc")
            .build();
    assertTrue(
        PostLogoutRedirects.allowed(client, "https://localhost:8000/signout-callback-oidc"));
  }
}
