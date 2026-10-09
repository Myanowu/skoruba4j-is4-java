package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

class Is4LogoutResponseHandlerTest {

  @Test
  void formLoginAuthenticationHasNoPostLogoutRedirect() {
    assertFalse(
        Is4LogoutResponseHandler.hasPostLogoutRedirect(
            UsernamePasswordAuthenticationToken.authenticated("user", "n/a", java.util.List.of())));
  }

  @Test
  void withoutPostLogoutStaysOnStsLoggedOutPageAndClearsSession() throws Exception {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("4sAdminWeb")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:8000/signin-oidc")
            .build();
    AuthenticationSuccessHandler handler =
        Is4LogoutResponseHandler.create(new InMemoryRegisteredClientRepository(client));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSecure(true);
    request.getSession(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    Authentication user =
        UsernamePasswordAuthenticationToken.authenticated("user", "n/a", java.util.List.of());
    OidcIdToken idToken =
        new OidcIdToken(
            "hint",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(60),
            java.util.Map.of("sub", "user"));
    OidcLogoutAuthenticationToken token =
        new OidcLogoutAuthenticationToken(idToken, user, "sid", "4sAdminWeb", null, null);
    handler.onAuthenticationSuccess(request, response, token);
    assertEquals("/login?logout", response.getRedirectedUrl());
    assertNull(request.getSession(false));
    assertEquals("4sAdminWeb", RpResume.read(requestWithCookies(request, response)));
  }

  @Test
  void withPostLogoutRedirectsThere() throws Exception {
    AuthenticationSuccessHandler handler = Is4LogoutResponseHandler.create();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    Authentication user =
        UsernamePasswordAuthenticationToken.authenticated("user", "n/a", java.util.List.of());
    OidcIdToken idToken =
        new OidcIdToken(
            "hint",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(60),
            java.util.Map.of("sub", "user"));
    OidcLogoutAuthenticationToken token =
        new OidcLogoutAuthenticationToken(
            idToken, user, "sid", "MyClientId", "https://localhost:6061/signout-callback-oidc", "st");
    handler.onAuthenticationSuccess(request, response, token);
    assertTrue(response.getRedirectedUrl().startsWith("https://localhost:6061/signout-callback-oidc"));
    assertTrue(response.getRedirectedUrl().contains("state=st"));
    assertNull(request.getSession(false));
  }

  private static MockHttpServletRequest requestWithCookies(
      MockHttpServletRequest original, MockHttpServletResponse response) {
    MockHttpServletRequest next = new MockHttpServletRequest();
    next.setCookies(response.getCookies());
    return next;
  }
}
