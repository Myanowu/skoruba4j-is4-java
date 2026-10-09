package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Writes the OAuth error body for a failed token-endpoint client authentication.
 *
 * <p>Spring Authorization Server's default handler keeps only {@code error} for {@code
 * invalid_client}, so callers such as ASP.NET OpenIdConnect see a null {@code error_description}.
 * This handler returns the reason the server already knows.
 */
public final class Is4ClientAuthenticationFailureHandler implements AuthenticationFailureHandler {
  private static final Logger log = LoggerFactory.getLogger(Is4ClientAuthenticationFailureHandler.class);
  private static final String CLIENT_AUTH_ERROR_URI =
      "https://datatracker.ietf.org/doc/html/rfc6749#section-3.2.1";

  private final HttpMessageConverter<OAuth2Error> errorResponseConverter =
      new OAuth2ErrorHttpMessageConverter();

  /** Returns HTTP 401 for {@code invalid_client}, with {@code error_description} filled in. */
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    OAuth2Error error = describe(exception);
    log.warn(
        "client authentication failed {} {}",
        error.getErrorCode(),
        error.getDescription() == null ? "" : error.getDescription());
    ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
    if (OAuth2ErrorCodes.INVALID_CLIENT.equals(error.getErrorCode())) {
      httpResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
    } else {
      httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
    }
    errorResponseConverter.write(error, null, httpResponse);
  }

  /**
   * Keeps a useful {@code error_description}. SAS detail strings are expanded; a missing
   * description gets a fallback so the client does not receive null.
   */
  static OAuth2Error describe(AuthenticationException exception) {
    OAuth2Error error = null;
    if (exception instanceof OAuth2AuthenticationException oauth) {
      error = oauth.getError();
    }
    String code =
        error == null || error.getErrorCode() == null || error.getErrorCode().isBlank()
            ? OAuth2ErrorCodes.INVALID_CLIENT
            : error.getErrorCode();
    String description = clarify(error == null ? null : error.getDescription(), code);
    String uri = error == null ? null : error.getUri();
    if (uri == null || uri.isBlank()) {
      uri = CLIENT_AUTH_ERROR_URI;
    }
    return new OAuth2Error(code, description, uri);
  }

  /** Turns SAS {@code Client authentication failed: <param>} into a sentence a caller can show. */
  static String clarify(String raw, String code) {
    if (raw != null && !raw.isBlank()) {
      return switch (raw) {
        case "Client authentication failed: client_id" -> "Unknown or disabled client_id.";
        case "Client authentication failed: authentication_method" ->
            "Client authentication method is not allowed for this client. "
                + "A public client (RequireClientSecret=false) uses client_id with PKCE; "
                + "a confidential client must send client_secret via client_secret_post or client_secret_basic.";
        case "Client authentication failed: client_secret" ->
            "client_secret does not match a SharedSecret stored for this client.";
        case "Client authentication failed: credentials" -> "client_secret was missing.";
        case "Client authentication failed: client_secret_expires_at" ->
            "client_secret has expired.";
        default -> raw;
      };
    }
    if (OAuth2ErrorCodes.INVALID_CLIENT.equals(code)) {
      return "Client authentication failed. Check client_id, whether this client requires a secret, "
          + "and that client_secret matches a non-expired SharedSecret.";
    }
    return code;
  }
}
