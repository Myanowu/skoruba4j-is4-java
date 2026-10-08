package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class Is4PublicClientAuthenticationProviderTest {

  private final RegisteredClient client =
      RegisteredClient.withId("1")
          .clientId("skoruba4j-admin")
          .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
          .authorizationGrantType(AuthorizationGrantType.PASSWORD)
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("https://localhost/cb")
          .scope("openid")
          .build();

  private final Is4PublicClientAuthenticationProvider provider =
      new Is4PublicClientAuthenticationProvider(new InMemoryRegisteredClientRepository(client));

  /** Password grant public client must authenticate without PKCE. */
  @Test
  void authenticatesPasswordGrantWithoutCodeVerifier() {
    OAuth2ClientAuthenticationToken request =
        new OAuth2ClientAuthenticationToken(
            "skoruba4j-admin",
            ClientAuthenticationMethod.NONE,
            null,
            Map.of(OAuth2ParameterNames.GRANT_TYPE, "password"));
    var result = provider.authenticate(request);
    assertNotNull(result);
    assertTrue(result.isAuthenticated());
  }

  /** authorization_code stays with SAS PublicClientAuthenticationProvider (PKCE). */
  @Test
  void ignoresAuthorizationCodeGrant() {
    OAuth2ClientAuthenticationToken request =
        new OAuth2ClientAuthenticationToken(
            "skoruba4j-admin",
            ClientAuthenticationMethod.NONE,
            null,
            Map.of(OAuth2ParameterNames.GRANT_TYPE, "authorization_code"));
    assertNull(provider.authenticate(request));
  }
}
