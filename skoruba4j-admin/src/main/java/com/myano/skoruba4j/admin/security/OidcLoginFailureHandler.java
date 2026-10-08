package com.myano.skoruba4j.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Do not send OIDC failures back to {@code /oauth2/authorization/sts}. Never put exception class
 * names in the login URL. Clear the in-progress authorization so the IdP can be retried.
 */
public final class OidcLoginFailureHandler implements AuthenticationFailureHandler {
  private static final Logger log = LoggerFactory.getLogger(OidcLoginFailureHandler.class);
  private final HttpSessionOAuth2AuthorizationRequestRepository authorizationRequests =
      new HttpSessionOAuth2AuthorizationRequestRepository();

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    String code = "oauth2_error";
    if (exception instanceof OAuth2AuthenticationException oauth && oauth.getError() != null) {
      OAuth2Error error = oauth.getError();
      if (error.getErrorCode() != null && !error.getErrorCode().isBlank()) {
        code = error.getErrorCode().trim();
      }
    }
    log.warn(
        "admin OIDC login failed [{}]: {}",
        code,
        exception == null ? "unknown" : exception.getMessage(),
        exception);
    authorizationRequests.removeAuthorizationRequest(request, response);
    new HttpSessionRequestCache().removeRequest(request, response);
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
    }
    new SecurityContextLogoutHandler()
        .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    if (code.length() > 64 || code.indexOf(' ') >= 0 || code.indexOf('.') >= 0) {
      code = "oauth2_error";
    }
    response.sendRedirect(
        "/login?error="
            + java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8));
  }
}
