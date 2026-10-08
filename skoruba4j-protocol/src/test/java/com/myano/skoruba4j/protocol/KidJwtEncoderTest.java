package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class KidJwtEncoderTest {

  @Test
  void compactJwtHeaderContainsKidEvenWhenCallerOmitsIt() {
    RSAKey key = SigningRsaKey.generate();
    KidJwtEncoder encoder =
        new KidJwtEncoder(
            new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))), key.getKeyID());
    Instant now = Instant.now();
    Jwt jwt =
        encoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder()
                    .subject("user")
                    .issuer("https://localhost:5051")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(60))
                    .build()));
    assertEquals(key.getKeyID(), jwt.getHeaders().get("kid"));
    String headerJson =
        new String(
            Base64.getUrlDecoder().decode(jwt.getTokenValue().split("\\.")[0]),
            StandardCharsets.UTF_8);
    assertTrue(headerJson.contains("\"kid\""));
    assertTrue(headerJson.contains(key.getKeyID()));
  }
}
