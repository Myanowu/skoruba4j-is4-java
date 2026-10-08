package com.myano.skoruba4j.protocol;

import java.time.Duration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * JWT lifetime checks shared by STS and resource servers. {@code strict} uses zero clock skew so an
 * expired token cannot be used (client must re-authenticate).
 */
public final class JwtExpiryValidators {
  private JwtExpiryValidators() {}

  public static OAuth2TokenValidator<Jwt> create(String issuer, EndSessionMode mode) {
    OAuth2TokenValidator<Jwt> baseline =
        issuer == null || issuer.isBlank()
            ? JwtValidators.createDefault()
            : JwtValidators.createDefaultWithIssuer(issuer);
    if (mode == null || mode.isCompatible()) {
      return baseline;
    }
    return new DelegatingOAuth2TokenValidator<>(
        baseline, new JwtTimestampValidator(Duration.ZERO));
  }
}
