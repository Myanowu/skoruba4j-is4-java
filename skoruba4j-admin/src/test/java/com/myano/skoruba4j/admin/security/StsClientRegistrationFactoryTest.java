package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.admin.config.IdserverProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

class StsClientRegistrationFactoryTest {

  @Test
  void usesIs4ConnectPathsAndSigninOidc() {
    IdserverProperties properties = new IdserverProperties();
    properties.setIssuerUri("http://127.0.0.1:5050");
    properties.getAdmin().setClientId("demo-admin");
    properties.getAdmin().setRedirectPath("/signin-oidc");
    properties.getAdmin().setScopes("openid,profile");
    ClientRegistration registration = StsClientRegistrationFactory.create(properties);
    assertEquals("demo-admin", registration.getClientId());
    assertEquals(ClientAuthenticationMethod.NONE, registration.getClientAuthenticationMethod());
    assertEquals("{baseUrl}/signin-oidc", registration.getRedirectUri());
    assertTrue(registration.getProviderDetails().getAuthorizationUri().endsWith("/connect/authorize"));
    assertTrue(registration.getProviderDetails().getTokenUri().endsWith("/connect/token"));
    assertEquals(
        "http://127.0.0.1:5050/connect/endsession",
        registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint"));
    assertTrue(registration.getScopes().contains("openid"));
    assertTrue(registration.getScopes().contains("profile"));
  }

  @Test
  void confidentialClientUsesBasicAuth() {
    IdserverProperties properties = new IdserverProperties();
    properties.setIssuerUri("http://127.0.0.1:5050");
    properties.getAdmin().setClientId("MyClientId");
    properties.getAdmin().setClientSecret("test-secret");
    properties.getAdmin().setRedirectPath("/signin-oidc");
    properties.getAdmin().setScopes("openid,profile,email,roles");
    ClientRegistration registration = StsClientRegistrationFactory.create(properties);
    assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, registration.getClientAuthenticationMethod());
    assertEquals("{baseUrl}/signin-oidc", registration.getRedirectUri());
    assertTrue(registration.getScopes().contains("roles"));
  }

  @Test
  void dropsScopesTheClientDoesNotAllow() {
    String[] kept =
        StsClientRegistrationFactory.scopesFor(
            new String[] {"openid", "profile", "email", "roles"}, Optional.empty(), "MyClientId");
    assertEquals("openid", kept[0]);
  }
}
