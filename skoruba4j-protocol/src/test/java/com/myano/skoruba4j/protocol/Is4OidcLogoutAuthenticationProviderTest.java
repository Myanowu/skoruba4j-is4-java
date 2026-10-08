package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

class Is4OidcLogoutAuthenticationProviderTest {

  @Test
  void honoursRegisteredPostLogoutRedirectFromIdTokenAudience() throws Exception {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("MyClientId")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:6061/signin-oidc")
            .postLogoutRedirectUri("https://localhost:6061/signout-callback-oidc")
            .build();
    var provider =
        new Is4OidcLogoutAuthenticationProvider(
            token -> {
              throw new JwtException("not this STS key");
            },
            new InMemoryRegisteredClientRepository(client));
    String hint = signedHint("MyClientId");
    var anonymous =
        new AnonymousAuthenticationToken(
            "anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    var request =
        new OidcLogoutAuthenticationToken(
            hint,
            anonymous,
            null,
            null,
            "https://localhost:6061/signout-callback-oidc",
            "state-1");
    OidcLogoutAuthenticationToken result =
        (OidcLogoutAuthenticationToken) provider.authenticate(request);
    assertTrue(result.isAuthenticated());
    assertEquals("https://localhost:6061/signout-callback-oidc", result.getPostLogoutRedirectUri());
    assertEquals("MyClientId", result.getClientId());
  }

  @Test
  void honoursAspNetSignOutCallbackWhenOnlyRedirectUriIsRegistered() throws Exception {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("MyClientId")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:6061/signin-oidc")
            .build();
    var provider =
        new Is4OidcLogoutAuthenticationProvider(
            token -> {
              throw new JwtException("not this STS key");
            },
            new InMemoryRegisteredClientRepository(client));
    var anonymous =
        new AnonymousAuthenticationToken(
            "anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    var request =
        new OidcLogoutAuthenticationToken(
            signedHint("MyClientId"),
            anonymous,
            null,
            null,
            "https://localhost:6061/signout-callback-oidc",
            "state-1");
    OidcLogoutAuthenticationToken result =
        (OidcLogoutAuthenticationToken) provider.authenticate(request);
    assertEquals("https://localhost:6061/signout-callback-oidc", result.getPostLogoutRedirectUri());
  }

  @Test
  void dropsUnregisteredPostLogoutRedirect() throws Exception {
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("MyClientId")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:6061/signin-oidc")
            .postLogoutRedirectUri("https://localhost:6061/signout-callback-oidc")
            .build();
    var provider =
        new Is4OidcLogoutAuthenticationProvider(
            token -> {
              throw new JwtException("not this STS key");
            },
            new InMemoryRegisteredClientRepository(client));
    var anonymous =
        new AnonymousAuthenticationToken(
            "anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    var request =
        new OidcLogoutAuthenticationToken(
            signedHint("MyClientId"),
            anonymous,
            null,
            null,
            "https://evil.example/steal",
            null);
    OidcLogoutAuthenticationToken result =
        (OidcLogoutAuthenticationToken) provider.authenticate(request);
    assertNull(result.getPostLogoutRedirectUri());
  }

  private static String signedHint(String audience) throws Exception {
    byte[] secret = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("user-1")
            .audience(List.of(audience))
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(secret));
    return jwt.serialize();
  }
}
