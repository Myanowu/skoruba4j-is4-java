package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

class Is4ClientAuthenticationFailureHandlerTest {

  private final Is4ClientAuthenticationFailureHandler handler =
      new Is4ClientAuthenticationFailureHandler();

  /** The token client sees the secret mismatch, not a null error_description. */
  @Test
  void invalidClientSecretIs401WithDescription() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationFailure(
        new MockHttpServletRequest(),
        response,
        new OAuth2AuthenticationException(
            new OAuth2Error(
                OAuth2ErrorCodes.INVALID_CLIENT,
                "Client authentication failed: client_secret",
                null)));
    assertEquals(401, response.getStatus());
    String body = response.getContentAsString();
    assertTrue(body.contains("\"error\":\"invalid_client\""));
    assertTrue(body.contains("client_secret does not match"));
    assertTrue(body.contains("error_description"));
    assertTrue(body.contains("error_uri"));
  }

  /** Public client rejected as client_secret_post explains the method, not a blank body. */
  @Test
  void authenticationMethodExplainsPublicVersusConfidential() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationFailure(
        new MockHttpServletRequest(),
        response,
        new OAuth2AuthenticationException(
            new OAuth2Error(
                OAuth2ErrorCodes.INVALID_CLIENT,
                "Client authentication failed: authentication_method",
                null)));
    String body = response.getContentAsString();
    assertTrue(body.contains("RequireClientSecret=false"));
    assertTrue(body.contains("client_secret_post"));
  }

  /** A code-only invalid_client still gets a description and error_uri. */
  @Test
  void missingDescriptionGetsFallback() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.onAuthenticationFailure(
        new MockHttpServletRequest(),
        response,
        new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT));
    String body = response.getContentAsString();
    assertTrue(body.contains("Client authentication failed. Check client_id"));
    assertTrue(body.contains("rfc6749"));
  }
}
