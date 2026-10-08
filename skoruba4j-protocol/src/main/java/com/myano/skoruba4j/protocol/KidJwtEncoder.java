package com.myano.skoruba4j.protocol;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

/**
 * IdentityModel requires a {@code kid} JOSE header. SAS/Nimbus can omit it unless the JWS header
 * and JWK both carry the same key id before encode.
 */
public final class KidJwtEncoder implements JwtEncoder {

  private final JwtEncoder delegate;
  private final String keyId;

  public KidJwtEncoder(JwtEncoder delegate, String keyId) {
    this.delegate = delegate;
    this.keyId = keyId;
  }

  @Override
  public Jwt encode(JwtEncoderParameters parameters) {
    if (parameters == null) {
      throw new IllegalArgumentException("parameters is required");
    }
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalStateException("Signing JWK is missing kid");
    }
    JwsHeader header = parameters.getJwsHeader();
    JwsHeader.Builder builder =
        header == null
            ? JwsHeader.with(SignatureAlgorithm.RS256)
            : JwsHeader.from(header);
    builder.keyId(keyId);
    Jwt jwt = delegate.encode(JwtEncoderParameters.from(builder.build(), parameters.getClaims()));
    Object kid = jwt.getHeaders().get("kid");
    if (kid == null || !keyId.equals(kid.toString())) {
      throw new IllegalStateException("Encoded JWT is missing kid");
    }
    return jwt;
  }
}
