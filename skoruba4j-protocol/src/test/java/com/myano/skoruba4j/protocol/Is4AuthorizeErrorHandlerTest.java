package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class Is4AuthorizeErrorHandlerTest {

  @Test
  void genericOauthErrorIs400Not500() throws Exception {
    Is4AuthorizeErrorHandler handler = new Is4AuthorizeErrorHandler();
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationFailure(
        new MockHttpServletRequest(),
        response,
        new OAuth2AuthenticationException(new OAuth2Error("invalid_request", "redirect_uri", null)));
    assertEquals(400, response.getStatus());
    String html = response.getContentAsString();
    assertTrue(html.contains("invalid_request"));
    assertTrue(html.contains("redirect_uri"));
    assertTrue(html.contains("not registered for this client"));
    assertTrue(html.contains("class=\"card\""));
    assertTrue(html.contains("href=\"/login/choose/other\""));
    assertTrue(html.contains("Use another account"));
    assertFalse(html.contains("ReturnUrl="));
  }

  @Test
  void authorizeFailureKeepsReturnUrlForAnotherAccount() throws Exception {
    String authorize =
        "/connect/authorize?response_type=code&client_id=4sAdminWeb"
            + "&redirect_uri=https://localhost:6061/signin-oidc&scope=openid";
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/connect/authorize");
    request.setQueryString(
        "response_type=code&client_id=4sAdminWeb&redirect_uri=https://localhost:6061/signin-oidc&scope=openid");
    request.addParameter("client_id", "4sAdminWeb");
    request.addParameter("redirect_uri", "https://localhost:6061/signin-oidc");
    MockHttpServletResponse response = new MockHttpServletResponse();
    new Is4AuthorizeErrorHandler()
        .onAuthenticationFailure(
            request,
            response,
            new OAuth2AuthenticationException(
                new OAuth2Error("invalid_request", "redirect_uri", null)));
    String html = response.getContentAsString();
    String encoded = URLEncoder.encode(authorize, StandardCharsets.UTF_8);
    assertTrue(html.contains("/login/choose/other?ReturnUrl=" + encoded));
    assertTrue(html.contains("/login?ReturnUrl=" + encoded));
    assertTrue(html.contains("4sAdminWeb"));
    assertTrue(html.contains("https://localhost:6061/signin-oidc"));
  }
}
