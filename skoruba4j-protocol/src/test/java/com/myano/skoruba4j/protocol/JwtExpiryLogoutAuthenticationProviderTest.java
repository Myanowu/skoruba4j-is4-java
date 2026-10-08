package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

class JwtExpiryLogoutAuthenticationProviderTest {

  @Test
  void expiredIdTokenHintIsInvalidToken() {
    var anonymous =
        new AnonymousAuthenticationToken(
            "anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    var request =
        new OidcLogoutAuthenticationToken("expired.jwt", anonymous, null, null, null, null);
    var provider =
        new JwtExpiryLogoutAuthenticationProvider(
            token -> {
              throw new JwtException("expired");
            },
            new org.springframework.security.authentication.AuthenticationProvider() {
              @Override
              public Authentication authenticate(Authentication authentication) {
                return authentication;
              }

              @Override
              public boolean supports(Class<?> authentication) {
                return true;
              }
            });
    OAuth2AuthenticationException ex =
        assertThrows(OAuth2AuthenticationException.class, () -> provider.authenticate(request));
    assertEquals(OAuth2ErrorCodes.INVALID_TOKEN, ex.getError().getErrorCode());
  }
}
