package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.sqlite.SQLiteDataSource;

class PersistedGrantOAuth2AuthorizationServiceTest {

  @TempDir Path temp;

  @Test
  void findByAccessTokenFindsAuthorizationEvenWhenTokenIsJwtShaped() throws Exception {
    Path db = temp.resolve("grants.sqlite");
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + db.toAbsolutePath());
    SqliteSchema.createEmpty(dataSource, TableStyle.SKORUBA);
    JdbcRepositories repos =
        new JdbcRepositories(dataSource, DbProvider.SQLITE, TableStyle.SKORUBA);
    RegisteredClient client =
        RegisteredClient.withId("rc-1")
            .clientId("demo-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://rp.example/cb")
            .scope("openid")
            .clientSettings(ClientSettings.builder().requireProofKey(true).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();
    PersistedGrantOAuth2AuthorizationService store =
        new PersistedGrantOAuth2AuthorizationService(
            repos.persistedGrants(), new InMemoryRegisteredClientRepository(client));

    String accessValue =
        "eyJhbGciOiJSUzI1NiJ9." + UUID.randomUUID() + "." + UUID.randomUUID();
    Instant now = Instant.now();
    OAuth2AccessToken access =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, accessValue, now, now.plusSeconds(3600));
    Map<String, Object> claims = Map.of("sub", "user-1", "iss", "https://sts.example");
    OAuth2Authorization authorization =
        OAuth2Authorization.withRegisteredClient(client)
            .id(UUID.randomUUID().toString())
            .principalName("user-1")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .attribute("state", "abc")
            .accessToken(access)
            .token(
                new org.springframework.security.oauth2.core.oidc.OidcIdToken(
                    "header.payload.sig", now, now.plusSeconds(3600), claims),
                meta -> meta.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims))
            .build();
    store.save(authorization);

    OAuth2Authorization found = store.findByToken(accessValue, OAuth2TokenType.ACCESS_TOKEN);
    assertNotNull(found, "UserInfo path requires findByToken(access_token)");
    assertEquals(authorization.getId(), found.getId());
    assertEquals("user-1", found.getPrincipalName());
    assertNotNull(found.getAccessToken());
    assertNotNull(found.getToken(org.springframework.security.oauth2.core.oidc.OidcIdToken.class));
    assertNull(store.findByToken("missing", OAuth2TokenType.ACCESS_TOKEN));
  }

  @Test
  void roundTripsAuthorizationRequestWithPkceForTokenExchange() throws Exception {
    Path db = temp.resolve("grants-pkce.sqlite");
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + db.toAbsolutePath());
    SqliteSchema.createEmpty(dataSource, TableStyle.SKORUBA);
    JdbcRepositories repos =
        new JdbcRepositories(dataSource, DbProvider.SQLITE, TableStyle.SKORUBA);
    RegisteredClient client =
        RegisteredClient.withId("rc-pkce")
            .clientId("skoruba_identity_admin")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:9002/login/oauth2/code/sts")
            .scope("openid")
            .clientSettings(ClientSettings.builder().requireProofKey(true).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();
    PersistedGrantOAuth2AuthorizationService store =
        new PersistedGrantOAuth2AuthorizationService(
            repos.persistedGrants(), new InMemoryRegisteredClientRepository(client));

    Instant now = Instant.now();
    String codeValue = "auth-code-" + UUID.randomUUID();
    OAuth2AuthorizationRequest authRequest =
        OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://localhost:5051/connect/authorize")
            .clientId(client.getClientId())
            .redirectUri("https://localhost:9002/login/oauth2/code/sts")
            .scopes(Set.of("openid"))
            .state("state-xyz")
            .additionalParameters(
                Map.of(
                    PkceParameterNames.CODE_CHALLENGE,
                    "challenge-abc",
                    PkceParameterNames.CODE_CHALLENGE_METHOD,
                    "S256"))
            .build();
    UsernamePasswordAuthenticationToken principal =
        UsernamePasswordAuthenticationToken.authenticated(
            "admin", "N/A", Set.of(new SimpleGrantedAuthority("SkorubaIdentityAdmin")));
    OAuth2Authorization authorization =
        OAuth2Authorization.withRegisteredClient(client)
            .id(UUID.randomUUID().toString())
            .principalName("admin")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid"))
            .attribute(OAuth2AuthorizationRequest.class.getName(), authRequest)
            .attribute(java.security.Principal.class.getName(), principal)
            .token(new OAuth2AuthorizationCode(codeValue, now, now.plusSeconds(300)))
            .build();
    store.save(authorization);

    OAuth2Authorization found =
        store.findByToken(codeValue, new OAuth2TokenType("code"));
    assertNotNull(found);
    OAuth2AuthorizationRequest restored =
        found.getAttribute(OAuth2AuthorizationRequest.class.getName());
    assertNotNull(restored, "PKCE token exchange requires OAuth2AuthorizationRequest");
    assertEquals("challenge-abc", restored.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE));
    assertEquals("S256", restored.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE_METHOD));
    Object restoredPrincipal = found.getAttribute(java.security.Principal.class.getName());
    assertTrue(restoredPrincipal instanceof UsernamePasswordAuthenticationToken);
    assertEquals("admin", ((UsernamePasswordAuthenticationToken) restoredPrincipal).getName());
  }

  @Test
  void roundTripsAccessTokenClaimsSoUserInfoIsActiveDoesNotClassCast() throws Exception {
    Path db = temp.resolve("grants-nbf.sqlite");
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + db.toAbsolutePath());
    SqliteSchema.createEmpty(dataSource, TableStyle.SKORUBA);
    JdbcRepositories repos =
        new JdbcRepositories(dataSource, DbProvider.SQLITE, TableStyle.SKORUBA);
    RegisteredClient client =
        RegisteredClient.withId("rc-nbf")
            .clientId("skoruba_identity_admin")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:9002/login/oauth2/code/sts")
            .scope("openid")
            .clientSettings(ClientSettings.builder().requireProofKey(true).build())
            .tokenSettings(TokenSettings.builder().build())
            .build();
    PersistedGrantOAuth2AuthorizationService store =
        new PersistedGrantOAuth2AuthorizationService(
            repos.persistedGrants(), new InMemoryRegisteredClientRepository(client));

    Instant now = Instant.now();
    String accessValue = "access-" + UUID.randomUUID();
    OAuth2AccessToken access =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            accessValue,
            now,
            now.plusSeconds(3600),
            Set.of("openid"));
    Map<String, Object> claims = new java.util.HashMap<>();
    claims.put("sub", "admin");
    claims.put("nbf", now.minusSeconds(5));
    claims.put("iat", now);
    claims.put("exp", now.plusSeconds(3600));
    OAuth2Authorization authorization =
        OAuth2Authorization.withRegisteredClient(client)
            .id(UUID.randomUUID().toString())
            .principalName("admin")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid"))
            .token(
                access,
                meta -> meta.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims))
            .token(
                new org.springframework.security.oauth2.core.oidc.OidcIdToken(
                    "h.p.s", now, now.plusSeconds(3600), Map.of("sub", "admin")),
                meta ->
                    meta.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME, Map.of("sub", "admin")))
            .build();
    store.save(authorization);

    OAuth2Authorization found = store.findByToken(accessValue, OAuth2TokenType.ACCESS_TOKEN);
    assertNotNull(found);
    assertNotNull(found.getAccessToken());
    assertTrue(
        found.getAccessToken().isActive(),
        "UserInfo calls isActive(); nbf must be Instant after JDBC round-trip");
    Object nbf =
        found.getAccessToken().getClaims() == null
            ? null
            : found.getAccessToken().getClaims().get("nbf");
    assertTrue(nbf instanceof Instant, "nbf restored as Instant, got " + nbf);
  }
}
