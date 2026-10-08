package com.myano.skoruba4j.protocol;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

/**
 * Strict RP-logout: {@code id_token_hint} must still be a valid, unexpired JWT, then the SAS logout
 * provider runs. Expired tokens are {@code invalid_token} so the client must sign in again.
 */
public final class JwtExpiryLogoutAuthenticationProvider implements AuthenticationProvider {
  private final JwtDecoder jwtDecoder;
  private final AuthenticationProvider delegate;

  public JwtExpiryLogoutAuthenticationProvider(
      JwtDecoder jwtDecoder, AuthenticationProvider delegate) {
    this.jwtDecoder = jwtDecoder;
    this.delegate = delegate;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    OidcLogoutAuthenticationToken request = (OidcLogoutAuthenticationToken) authentication;
    try {
      jwtDecoder.decode(request.getIdTokenHint());
    } catch (RuntimeException ex) {
      OAuth2Error error =
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "id_token_hint is expired or invalid; the client must authenticate again",
              "https://openid.net/specs/openid-connect-rpinitiated-1_0.html#ValidationAndErrorHandling");
      throw new OAuth2AuthenticationException(error);
    }
    return delegate.authenticate(authentication);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return delegate.supports(authentication);
  }
}
