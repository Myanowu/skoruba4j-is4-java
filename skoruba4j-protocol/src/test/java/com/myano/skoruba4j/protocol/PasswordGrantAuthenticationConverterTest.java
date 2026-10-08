package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

class PasswordGrantAuthenticationConverterTest {

  private final PasswordGrantAuthenticationConverter converter =
      new PasswordGrantAuthenticationConverter();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ignoresOtherGrantTypes() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "client_credentials");
    assertNull(converter.convert(request));
  }

  @Test
  void requiresUsername() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("client", "n/a"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "password");
    request.setParameter(OAuth2ParameterNames.PASSWORD, "x");
    assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(request));
  }

  @Test
  void convertsPasswordGrant() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("client", "n/a"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "password");
    request.setParameter(OAuth2ParameterNames.USERNAME, "demo");
    request.setParameter(OAuth2ParameterNames.PASSWORD, "Passw0rd!");
    request.setParameter(OAuth2ParameterNames.SCOPE, "openid profile");
    var auth = converter.convert(request);
    assertTrue(auth instanceof PasswordGrantAuthenticationToken token);
    PasswordGrantAuthenticationToken token = (PasswordGrantAuthenticationToken) auth;
    assertEquals("demo", token.getUsername());
    assertEquals("Passw0rd!", token.getPassword());
    assertEquals("openid profile", token.getAdditionalParameters().get(OAuth2ParameterNames.SCOPE));
  }
}
