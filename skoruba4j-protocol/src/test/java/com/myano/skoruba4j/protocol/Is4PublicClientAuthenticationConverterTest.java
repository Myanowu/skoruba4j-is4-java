package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;

class Is4PublicClientAuthenticationConverterTest {

  private final Is4PublicClientAuthenticationConverter converter =
      new Is4PublicClientAuthenticationConverter();

  /** Password grant with only client_id should authenticate as a public client. */
  @Test
  void convertsPasswordGrantPublicClient() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "password");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "skoruba4j-admin");
    request.setParameter(OAuth2ParameterNames.USERNAME, "demo");
    request.setParameter(OAuth2ParameterNames.PASSWORD, "x");
    Authentication auth = converter.convert(request);
    assertNotNull(auth);
    assertTrue(auth instanceof OAuth2ClientAuthenticationToken);
    OAuth2ClientAuthenticationToken client = (OAuth2ClientAuthenticationToken) auth;
    assertEquals("skoruba4j-admin", client.getPrincipal());
    assertEquals(ClientAuthenticationMethod.NONE, client.getClientAuthenticationMethod());
  }

  /** Confidential-looking requests leave client_secret to other converters. */
  @Test
  void ignoresWhenClientSecretPresent() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "password");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "skoruba4j-admin");
    request.setParameter(OAuth2ParameterNames.CLIENT_SECRET, "secret");
    assertNull(converter.convert(request));
  }

  /** PKCE public clients stay with the SAS converter. */
  @Test
  void ignoresWhenCodeVerifierPresent() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "password");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "skoruba4j-admin");
    request.setParameter("code_verifier", "verifier");
    assertNull(converter.convert(request));
  }

  /** Public refresh without a secret is client_id only (IS4 RequireClientSecret=false). */
  @Test
  void convertsRefreshGrantPublicClient() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "refresh_token");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "skoruba4j-admin");
    request.setParameter(OAuth2ParameterNames.REFRESH_TOKEN, "refresh");
    Authentication auth = converter.convert(request);
    assertNotNull(auth);
    assertEquals(
        ClientAuthenticationMethod.NONE,
        ((OAuth2ClientAuthenticationToken) auth).getClientAuthenticationMethod());
  }

  @Test
  void ignoresAuthorizationCode() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "authorization_code");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "skoruba4j-admin");
    assertNull(converter.convert(request));
  }
}
