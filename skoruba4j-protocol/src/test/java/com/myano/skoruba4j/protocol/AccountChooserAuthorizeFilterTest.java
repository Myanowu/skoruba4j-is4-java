package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AccountChooserAuthorizeFilterTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void offersChooserForSignedInBrowserAuthorize() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("alice", "x", List.of()));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/connect/authorize");
    request.addHeader("Accept", "text/html");
    request.setParameter("response_type", "code");
    assertTrue(AccountChooserAuthorizeFilter.shouldOfferChooser(request));
  }

  @Test
  void skipsPromptNone() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("alice", "x", List.of()));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/connect/authorize");
    request.addHeader("Accept", "text/html");
    request.setParameter("prompt", "none");
    assertFalse(AccountChooserAuthorizeFilter.shouldOfferChooser(request));
  }
}
