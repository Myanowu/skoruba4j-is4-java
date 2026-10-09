package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

class Is4PublicClientSecretOptionalConverterTest {

  private final RegisteredClient publicClient =
      RegisteredClient.withId("1")
          .clientId("public-web")
          .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
          .redirectUri("https://localhost:8000/signin-oidc")
          .scope("openid")
          .clientSettings(ClientSettings.builder().requireProofKey(true).build())
          .build();

  private final RegisteredClient confidentialClient =
      RegisteredClient.withId("2")
          .clientId("confidential-web")
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          .clientSecret("hashed-secret")
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("https://localhost:8000/signin-oidc")
          .scope("openid")
          .build();

  private final Is4PublicClientSecretOptionalConverter converter =
      new Is4PublicClientSecretOptionalConverter(
          new InMemoryRegisteredClientRepository(publicClient, confidentialClient));

  /** ASP.NET posts client_secret even when IS4 RequireClientSecret is false. */
  @Test
  void publicClientPostedSecretAuthenticatesAsNoneAndKeepsPkce() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "authorization_code");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "public-web");
    request.setParameter(OAuth2ParameterNames.CLIENT_SECRET, "posted-but-not-required");
    request.setParameter(OAuth2ParameterNames.CODE, "auth-code");
    request.setParameter(PkceParameterNames.CODE_VERIFIER, "verifier");
    request.setParameter(OAuth2ParameterNames.REDIRECT_URI, "https://localhost:8000/signin-oidc");

    OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) converter.convert(request);

    assertEquals("public-web", token.getPrincipal());
    assertEquals(ClientAuthenticationMethod.NONE, token.getClientAuthenticationMethod());
    assertEquals("verifier", token.getAdditionalParameters().get(PkceParameterNames.CODE_VERIFIER));
    assertEquals("auth-code", token.getAdditionalParameters().get(OAuth2ParameterNames.CODE));
    assertFalse(token.getAdditionalParameters().containsKey(OAuth2ParameterNames.CLIENT_SECRET));
  }

  /** Refresh from the same public client must not be forced through client_secret_post. */
  @Test
  void publicClientRefreshWithSecretAuthenticatesAsNone() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "refresh_token");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "public-web");
    request.setParameter(OAuth2ParameterNames.CLIENT_SECRET, "posted-but-not-required");
    request.setParameter(OAuth2ParameterNames.REFRESH_TOKEN, "refresh");

    OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) converter.convert(request);

    assertEquals(ClientAuthenticationMethod.NONE, token.getClientAuthenticationMethod());
    assertEquals("refresh", token.getAdditionalParameters().get(OAuth2ParameterNames.REFRESH_TOKEN));
  }

  /** HTTP Basic on a public client is the same IS4 skip-secret case. */
  @Test
  void publicClientBasicSecretAuthenticatesAsNone() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "authorization_code");
    request.setParameter(OAuth2ParameterNames.CODE, "auth-code");
    request.setParameter(PkceParameterNames.CODE_VERIFIER, "verifier");
    String raw = Base64.getEncoder().encodeToString("public-web:ignored".getBytes(StandardCharsets.UTF_8));
    request.addHeader("Authorization", "Basic " + raw);

    OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) converter.convert(request);

    assertEquals("public-web", token.getPrincipal());
    assertEquals(ClientAuthenticationMethod.NONE, token.getClientAuthenticationMethod());
  }

  /** Confidential clients keep ClientSecretPost / Basic. */
  @Test
  void confidentialClientIsLeftToSecretConverters() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "authorization_code");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "confidential-web");
    request.setParameter(OAuth2ParameterNames.CLIENT_SECRET, "plain");
    request.setParameter(OAuth2ParameterNames.CODE, "auth-code");
    assertNull(converter.convert(request));
  }

  /** No secret presented: PKCE and password converters keep their existing paths. */
  @Test
  void noSecretIsIgnored() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "authorization_code");
    request.setParameter(OAuth2ParameterNames.CLIENT_ID, "public-web");
    request.setParameter(OAuth2ParameterNames.CODE, "auth-code");
    request.setParameter(PkceParameterNames.CODE_VERIFIER, "verifier");
    assertNull(converter.convert(request));
  }
}
