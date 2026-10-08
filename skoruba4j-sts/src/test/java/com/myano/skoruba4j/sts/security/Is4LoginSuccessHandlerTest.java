package com.myano.skoruba4j.sts.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.protocol.AccountChooser;
import com.myano.skoruba4j.protocol.RpResume;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.savedrequest.NullRequestCache;

class Is4LoginSuccessHandlerTest {

  @Test
  void directLoginWithoutReturnUrlStaysOnStsHome() {
    Is4LoginSuccessHandler handler = new Is4LoginSuccessHandler();
    handler.setRequestCache(new NullRequestCache());
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse seed = new MockHttpServletResponse();
    RpResume.store(request, seed, "MyClientId");
    request.setCookies(seed.getCookies());
    assertEquals("/", handler.determineTargetUrl(request, new MockHttpServletResponse()));
  }

  @Test
  void authorizeReturnUrlGoesStraightToAuthorize() {
    Is4LoginSuccessHandler handler = new Is4LoginSuccessHandler();
    handler.setRequestCache(new NullRequestCache());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("ReturnUrl", "/connect/authorize?client_id=MyClientId");
    assertEquals(
        "/connect/authorize?client_id=MyClientId",
        handler.determineTargetUrl(request, new MockHttpServletResponse()));
  }

  @Test
  void successRedirectMarksChooserApproved() throws Exception {
    Is4LoginSuccessHandler handler = new Is4LoginSuccessHandler();
    handler.setRequestCache(new NullRequestCache());
    MockHttpServletRequest request = new MockHttpServletRequest();
    String returnUrl = "/connect/authorize?client_id=MyClientId";
    request.setParameter("ReturnUrl", returnUrl);
    MockHttpServletResponse response = new MockHttpServletResponse();
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated("user", "n/a", java.util.List.of());
    handler.onAuthenticationSuccess(request, response, auth);
    assertEquals(returnUrl, response.getRedirectedUrl());
    assertNotNull(request.getSession(false));
    assertTrue(AccountChooser.isApproved(request.getSession(false), returnUrl));
  }
}
