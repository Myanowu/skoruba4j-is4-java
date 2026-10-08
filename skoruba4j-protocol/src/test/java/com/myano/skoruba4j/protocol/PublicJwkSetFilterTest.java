package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PublicJwkSetFilterTest {

  @Test
  void is4AndOauth2PathsArePublicUtf8Jwks() throws Exception {
    RSAKey key = SigningRsaKey.generate();
    PublicJwkSetFilter filter = new PublicJwkSetFilter(new ImmutableJWKSet<>(new JWKSet(key)));
    for (String path : new String[] {Is4Paths.JWKS, Is4Paths.OAUTH2_JWKS}) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
      request.setRequestURI(path);
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, new MockFilterChain());
      assertEquals(200, response.getStatus());
      assertTrue(response.getContentType().contains("jwk-set+json"));
      String body = response.getContentAsString();
      assertTrue(body.contains("\"kid\":\"" + key.getKeyID() + "\""));
      assertTrue(body.contains("\"kty\":\"RSA\""));
      assertFalse(body.contains("\"d\":"));
    }
  }

  @Test
  void otherPathsPassThrough() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
    request.setRequestURI("/login");
    assertFalse(PublicJwkSetFilter.matches(request));
  }
}
