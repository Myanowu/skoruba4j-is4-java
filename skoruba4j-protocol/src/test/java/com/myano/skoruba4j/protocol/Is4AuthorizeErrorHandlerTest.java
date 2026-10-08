package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    assertTrue(response.getContentAsString().contains("invalid_request"));
    assertTrue(response.getContentAsString().contains("redirect_uri"));
  }
}
