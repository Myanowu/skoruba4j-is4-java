package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * RP-initiated logout: honour {@code post_logout_redirect_uri} when the client registered one;
 * otherwise send the browser to the STS login page so SSO cannot bounce back in.
 */
public final class Is4LogoutResponseHandler {
  private Is4LogoutResponseHandler() {}

  public static AuthenticationSuccessHandler create() {
    OidcLogoutAuthenticationSuccessHandler oidc = new OidcLogoutAuthenticationSuccessHandler();
    return (request, response, authentication) -> {
      if (hasPostLogoutRedirect(authentication)) {
        oidc.onAuthenticationSuccess(request, response, authentication);
        return;
      }
      if (authentication instanceof OidcLogoutAuthenticationToken token) {
        RpResume.store(request, response, token.getClientId());
      }
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
      SecurityContextHolder.clearContext();
      response.sendRedirect("/login?logout");
    };
  }

  static boolean hasPostLogoutRedirect(Authentication authentication) {
    if (!(authentication instanceof OidcLogoutAuthenticationToken token)) {
      return false;
    }
    String uri = token.getPostLogoutRedirectUri();
    return uri != null && !uri.isBlank();
  }
}
