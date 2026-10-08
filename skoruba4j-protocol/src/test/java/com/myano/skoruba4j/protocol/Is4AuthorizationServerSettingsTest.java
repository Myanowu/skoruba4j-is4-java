package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

class Is4AuthorizationServerSettingsTest {

  @Test
  void usesIdentityServer4Paths() {
    AuthorizationServerSettings settings =
        Is4AuthorizationServerSettings.create("http://127.0.0.1:5050");
    assertEquals("http://127.0.0.1:5050", settings.getIssuer());
    assertEquals(Is4Paths.AUTHORIZE, settings.getAuthorizationEndpoint());
    assertEquals(Is4Paths.TOKEN, settings.getTokenEndpoint());
    assertEquals(Is4Paths.USERINFO, settings.getOidcUserInfoEndpoint());
    assertEquals(Is4Paths.INTROSPECT, settings.getTokenIntrospectionEndpoint());
    assertEquals(Is4Paths.REVOCATION, settings.getTokenRevocationEndpoint());
    assertEquals(Is4Paths.JWKS, settings.getJwkSetEndpoint());
    assertEquals(Is4Paths.END_SESSION, settings.getOidcLogoutEndpoint());
  }
}

class Is4ClientSecretPasswordEncoderTest {

  @Test
  void matchesSha256Base64OfSecret() {
    Is4ClientSecretPasswordEncoder encoder = new Is4ClientSecretPasswordEncoder();
    String secret = "MyClientSecret";
    String stored = Is4ClientSecretPasswordEncoder.sha256Base64(secret);
    assertTrue(encoder.matches(secret, stored));
    assertTrue(encoder.matches(secret, secret));
  }
}
