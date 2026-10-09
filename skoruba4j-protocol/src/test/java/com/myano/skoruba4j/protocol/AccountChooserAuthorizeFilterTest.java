package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AccountChooserAuthorizeFilterTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void skipsChooserAfterFreshPasswordLogin() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("alice", "x", List.of()));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/connect/authorize");
    request.setQueryString("client_id=4sAdminWeb&response_type=code&redirect_uri=https://localhost:8000/signin-oidc");
    request.addHeader("Accept", "text/html");
    AccountChooser.markFreshLogin(request.getSession(true));
    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean[] continued = {false};
    new AccountChooserAuthorizeFilter(true)
        .doFilter(request, response, (req, res) -> continued[0] = true);
    assertTrue(continued[0]);
    assertTrue(response.getRedirectedUrl() == null || response.getRedirectedUrl().isBlank());
  }

  @Test
  void offersChooserForExistingSsoWithoutFreshLogin() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("alice", "x", List.of()));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/connect/authorize");
    request.setQueryString("client_id=4sAdminWeb&response_type=code");
    request.addHeader("Accept", "text/html");
    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean[] continued = {false};
    new AccountChooserAuthorizeFilter(true)
        .doFilter(request, response, (req, res) -> continued[0] = true);
    assertFalse(continued[0]);
    assertTrue(response.getRedirectedUrl().startsWith("/login/choose"));
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

  /** Control can disable the picker; authorize must not redirect to /login/choose. */
  @Test
  void disabledFilterPassesThroughEvenWhenSignedIn() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("alice", "x", List.of()));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/connect/authorize");
    request.setQueryString("client_id=4sWeb&response_type=code");
    request.addHeader("Accept", "text/html");
    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean[] continued = {false};
    new AccountChooserAuthorizeFilter(false)
        .doFilter(request, response, (req, res) -> continued[0] = true);
    assertTrue(continued[0]);
    assertTrue(response.getRedirectedUrl() == null || response.getRedirectedUrl().isBlank());
  }
}
