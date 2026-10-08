package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

class JwsHeaderKidCustomizerTest {

  @Test
  void writesKidOntoTheSameHeaderBuilderJwtGeneratorUses() {
    JwsHeader.Builder header = JwsHeader.with(SignatureAlgorithm.RS256);
    Instant now = Instant.now();
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .subject("user")
            .issuer("https://localhost:5051")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60));
    JwtEncodingContext context = JwtEncodingContext.with(header, claims).build();
    JwsHeaderKidCustomizer.apply(context, "expected-kid");
    assertEquals("expected-kid", header.build().getKeyId());
  }
}
