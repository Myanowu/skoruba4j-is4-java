package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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

  /**
   * Authorize failures stay a 400 page when the redirect cannot be trusted. The page keeps the
   * original authorize URL so the browser can sign in as a different account and retry.
   */
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
    response.getWriter().write(page(request, error));
  }

  /**
   * Browser page for an authorize failure that cannot be sent back to the client. Matches the STS
   * sign-in card and keeps a way to try a different account.
   */
  static String page(HttpServletRequest request, OAuth2Error error) {
    String code = error.getErrorCode() == null ? "invalid_request" : error.getErrorCode();
    String description = error.getDescription() == null ? "" : error.getDescription().trim();
    String clientId = param(request, OAuth2ParameterNames.CLIENT_ID);
    String callback = param(request, OAuth2ParameterNames.REDIRECT_URI);
    StringBuilder details = new StringBuilder();
    details.append(row("Status", "400"));
    details.append(row("Error", code));
    if (!description.isBlank()) {
      details.append(row("Detail", description));
    }
    if (!clientId.isBlank()) {
      details.append(row("Client", clientId));
    }
    if (!callback.isBlank()) {
      details.append(row("Callback", callback));
    }
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        + "<title>Sign-in could not finish</title>"
        + "<style>"
        + css()
        + "</style></head><body class=\"auth\"><main class=\"card\">"
        + "<p class=\"kicker\">Skoruba4j STS</p>"
        + "<h1>Sign-in could not finish</h1>"
        + "<p class=\"lead\">"
        + esc(plainExplanation(code, description))
        + "</p>"
        + "<dl>"
        + details
        + "</dl>"
        + recoveryLinks(request)
        + "</main></body></html>";
  }

  /** Plain meaning of the OAuth error. Redirect rejection happens before the account is checked. */
  static String plainExplanation(String code, String description) {
    String detail = description == null ? "" : description.toLowerCase(Locale.ROOT);
    if ("invalid_request".equals(code) && detail.contains("redirect_uri")) {
      return "The callback address is not registered for this client. "
          + "Sign-in stopped before the account was checked, so this is not a missing role.";
    }
    if ("invalid_request".equals(code)) {
      return "The authorization request was rejected. The account was not checked.";
    }
    return "The authorization request was rejected before a code could be issued.";
  }

  private static String row(String label, String value) {
    return "<div><dt>" + esc(label) + "</dt><dd>" + esc(value) + "</dd></div>";
  }

  private static String param(HttpServletRequest request, String name) {
    if (request == null) {
      return "";
    }
    String value = request.getParameter(name);
    return value == null ? "" : value.trim();
  }

  /**
   * Sign out the current STS session and return to the login form. When this request is still a
   * safe authorize URL, keep it as {@code ReturnUrl} so the next account can finish the same login.
   */
  static String recoveryLinks(HttpServletRequest request) {
    String other = AccountChooser.CHOOSE_PATH + "/other";
    String login = "/login";
    String returnUrl = AccountChooser.currentAuthorizeReturnUrl(request);
    if (Is4ReturnUrls.isSafe(returnUrl)) {
      String encoded = URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
      other = other + "?ReturnUrl=" + encoded;
      login = login + "?ReturnUrl=" + encoded;
    }
    return "<a class=\"btn\" href=\""
        + esc(other)
        + "\">Use another account</a>"
        + "<p class=\"muted\"><a href=\""
        + esc(login)
        + "\">Back to sign in</a></p>";
  }

  private static String css() {
    return """
        :root{--ink:#111827;--muted:#6b7280;--line:#e5e7eb;--card:#fff;--err:#b91c1c;--err-bg:#fef2f2}
        *{box-sizing:border-box}
        body{margin:0;font-family:Segoe UI,system-ui,sans-serif;color:var(--ink);line-height:1.45}
        body.auth{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:1.5rem;
          background:radial-gradient(900px 320px at 50% -8%,#fee2e2 0%,#eef2f7 46%)}
        .card{width:min(28rem,100%);background:var(--card);border-radius:16px;
          box-shadow:0 16px 40px rgba(15,23,42,.08);padding:2rem}
        .kicker{margin:0 0 .35rem;font-size:.75rem;letter-spacing:.12em;text-transform:uppercase;color:var(--err);font-weight:700}
        h1{margin:0 0 .35rem;font-size:1.65rem}
        .lead{margin:0 0 1.15rem;color:var(--muted)}
        dl{margin:0 0 1.25rem;border:1px solid var(--line);border-radius:12px;overflow:hidden}
        dl div{display:grid;grid-template-columns:6.5rem 1fr;gap:.75rem;padding:.7rem .9rem;background:#fff}
        dl div+div{border-top:1px solid var(--line)}
        dt{margin:0;color:var(--muted);font-size:.82rem;font-weight:650}
        dd{margin:0;font-size:.92rem;word-break:break-all}
        a.btn{display:block;width:100%;background:#111827;color:#fff;border-radius:10px;padding:.85rem 1rem;
          font-weight:650;text-align:center;text-decoration:none}
        a.btn:hover{background:#000}
        .muted{margin:.9rem 0 0;text-align:center;color:var(--muted);font-size:.92rem}
        a{color:#2563eb;text-decoration:none}
        a:hover{text-decoration:underline}
        """;
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
