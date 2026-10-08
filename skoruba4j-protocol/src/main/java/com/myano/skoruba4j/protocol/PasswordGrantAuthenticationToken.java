package com.myano.skoruba4j.protocol;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

/** Resource owner password credentials ({@code grant_type=password}). */
public final class PasswordGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {
  public static final AuthorizationGrantType PASSWORD = AuthorizationGrantType.PASSWORD;

  private final String username;
  private final String password;

  public PasswordGrantAuthenticationToken(
      Authentication clientPrincipal,
      String username,
      String password,
      Map<String, Object> additionalParameters) {
    super(PASSWORD, clientPrincipal, additionalParameters);
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
