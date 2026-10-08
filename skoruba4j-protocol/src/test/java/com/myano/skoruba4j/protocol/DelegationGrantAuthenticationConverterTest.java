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

class DelegationGrantAuthenticationConverterTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ignoresOtherGrantTypes() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("grant_type", "authorization_code");
    assertNull(new DelegationGrantAuthenticationConverter().convert(request));
  }

  @Test
  void readsTokenFormField() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("client", "secret"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("grant_type", "delegation");
    request.setParameter("token", " access.jwt ");
    request.setParameter("scope", "api1");
    DelegationGrantAuthenticationToken converted =
        (DelegationGrantAuthenticationToken)
            new DelegationGrantAuthenticationConverter().convert(request);
    assertEquals("access.jwt", converted.getSubjectToken());
    assertEquals("api1", converted.getAdditionalParameters().get("scope"));
    assertEquals("delegation", converted.getGrantType().getValue());
  }

  @Test
  void requiresToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("grant_type", "delegation");
    assertThrows(
        OAuth2AuthenticationException.class,
        () -> new DelegationGrantAuthenticationConverter().convert(request));
  }

  @Test
  void resolveScopesIntersectsRequested() {
    var client =
        org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId("1")
            .clientId("demo")
            .authorizationGrantType(DelegationGrantAuthenticationToken.DELEGATION)
            .scope("api1")
            .scope("openid")
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("client", "secret"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("grant_type", "delegation");
    request.setParameter("token", "t");
    request.setParameter("scope", "api1 extra");
    DelegationGrantAuthenticationToken token =
        (DelegationGrantAuthenticationToken)
            new DelegationGrantAuthenticationConverter().convert(request);
    assertTrue(
        DelegationGrantAuthenticationProvider.resolveScopes(client, token).contains("api1"));
    assertEquals(1, DelegationGrantAuthenticationProvider.resolveScopes(client, token).size());
  }
}
