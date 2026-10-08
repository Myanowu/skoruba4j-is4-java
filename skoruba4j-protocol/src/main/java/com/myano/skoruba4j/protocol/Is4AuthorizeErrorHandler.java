package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * SAS default handler ClassCasts any {@link OAuth2AuthenticationException} to the authorize-specific
 * type, which turns a normal OAuth error into HTTP 500. Keep authorize failures as 400 or a client
 * redirect.
 */
public final class Is4AuthorizeErrorHandler implements AuthenticationFailureHandler {
  private static final Logger log = LoggerFactory.getLogger(Is4AuthorizeErrorHandler.class);
  private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    OAuth2Error error = errorOf(exception);
    log.warn(
        "authorize failed {} {} {}",
        error.getErrorCode(),
        error.getDescription() == null ? "" : error.getDescription(),
        exception.toString());
    String redirectUri = redirectUri(exception);
    if (redirectUri != null && !redirectUri.isBlank()) {
      UriComponentsBuilder uriBuilder =
          UriComponentsBuilder.fromUriString(redirectUri)
              .queryParam(OAuth2ParameterNames.ERROR, error.getErrorCode());
      if (error.getDescription() != null && !error.getDescription().isBlank()) {
        uriBuilder.queryParam(OAuth2ParameterNames.ERROR_DESCRIPTION, error.getDescription());
      }
      String state = state(exception);
      if (state != null && !state.isBlank()) {
        uriBuilder.queryParam(OAuth2ParameterNames.STATE, state);
      }
      redirectStrategy.sendRedirect(request, response, uriBuilder.toUriString());
      return;
    }
    response.setStatus(HttpStatus.BAD_REQUEST.value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("text/html;charset=UTF-8");
    response
        .getWriter()
        .write(
            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Sign-in could not finish</title>"
                + "<style>body{font-family:Segoe UI,sans-serif;margin:2rem auto;max-width:36rem}</style></head><body>"
                + "<h1>Sign-in could not finish</h1><p>Status: <code>400</code></p><p><code>"
                + esc(error.getErrorCode())
                + "</code>"
                + (error.getDescription() == null || error.getDescription().isBlank()
                    ? ""
                    : " - " + esc(error.getDescription()))
                + "</p><p><a href=\"/login\">Back to sign in</a></p></body></html>");
  }

  static OAuth2Error errorOf(AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauth
        && oauth.getError() != null
        && oauth.getError().getErrorCode() != null) {
      return oauth.getError();
    }
    String message = exception == null ? "server_error" : exception.getMessage();
    return new OAuth2Error("server_error", message, null);
  }

  static String redirectUri(AuthenticationException exception) {
    if (exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException ex) {
      OAuth2AuthorizationCodeRequestAuthenticationToken token =
          ex.getAuthorizationCodeRequestAuthentication();
      if (token != null && token.getRedirectUri() != null && !token.getRedirectUri().isBlank()) {
        return token.getRedirectUri();
      }
    }
    return null;
  }

  static String state(AuthenticationException exception) {
    if (exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException ex) {
      OAuth2AuthorizationCodeRequestAuthenticationToken token =
          ex.getAuthorizationCodeRequestAuthentication();
      if (token != null) {
        return token.getState();
      }
    }
    return null;
  }

  static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
