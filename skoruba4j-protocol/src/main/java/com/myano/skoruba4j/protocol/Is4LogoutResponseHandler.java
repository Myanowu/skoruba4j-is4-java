package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * RP-initiated logout aligned with IdentityServer4 / Skoruba STS Identity:
 *
 * <ol>
 *   <li>Sign out the interactive STS user (local cookie / session), like {@code
 *       SignInManager.SignOutAsync()}.
 *   <li>If {@code post_logout_redirect_uri} is registered, redirect there.
 *   <li>Otherwise stay on STS {@code /login?logout} (LoggedOut). Do <strong>not</strong> bounce to
 *       the RP {@code /login} — that immediately restarts OIDC and looks like logout failed.
 * </ol>
 */
public final class Is4LogoutResponseHandler {
  private Is4LogoutResponseHandler() {}

  public static AuthenticationSuccessHandler create() {
    return create(null);
  }

  public static AuthenticationSuccessHandler create(RegisteredClientRepository clients) {
    RedirectStrategy redirects = new DefaultRedirectStrategy();
    SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();
    return (request, response, authentication) -> {
      clearInteractiveSession(request, response, authentication, sessionLogout);
      if (authentication instanceof OidcLogoutAuthenticationToken token) {
        String clientId = token.getClientId();
        if (clientId != null && !clientId.isBlank() && !hasPostLogoutRedirect(token)) {
          RpResume.store(request, response, clientId);
        }
      }
      String postLogout = postLogoutRedirect(authentication);
      if (postLogout != null) {
        redirects.sendRedirect(request, response, postLogout);
        return;
      }
      redirects.sendRedirect(request, response, "/login?logout");
    };
  }

  static boolean hasPostLogoutRedirect(Authentication authentication) {
    return postLogoutRedirect(authentication) != null;
  }

  static String postLogoutRedirect(Authentication authentication) {
    if (!(authentication instanceof OidcLogoutAuthenticationToken token)) {
      return null;
    }
    String uri = token.getPostLogoutRedirectUri();
    if (uri == null || uri.isBlank()) {
      return null;
    }
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uri);
    String state = token.getState();
    if (state != null && !state.isBlank()) {
      builder.queryParam(
          OAuth2ParameterNames.STATE, UriUtils.encode(state, StandardCharsets.UTF_8));
    }
    return builder.build(true).toUriString();
  }

  /**
   * Same rule as SAS {@code OidcLogoutAuthenticationSuccessHandler}: logout the user principal, not
   * the logout token itself.
   */
  static void clearInteractiveSession(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication,
      SecurityContextLogoutHandler sessionLogout) {
    Authentication user = interactiveUser(authentication);
    if (user != null) {
      sessionLogout.logout(request, response, user);
    } else {
      sessionLogout.logout(request, response, authentication);
    }
    HttpSession session = request.getSession(false);
    if (session != null) {
      try {
        session.invalidate();
      } catch (IllegalStateException ignored) {
        // already invalidated
      }
    }
    SecurityContextHolder.clearContext();
  }

  private static Authentication interactiveUser(Authentication authentication) {
    if (!(authentication instanceof OidcLogoutAuthenticationToken token)) {
      return null;
    }
    if (!token.isPrincipalAuthenticated()) {
      return null;
    }
    Object principal = token.getPrincipal();
    return principal instanceof Authentication auth ? auth : null;
  }
}
