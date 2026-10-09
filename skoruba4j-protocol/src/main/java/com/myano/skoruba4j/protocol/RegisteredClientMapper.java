package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.configstore.ClientConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

public final class RegisteredClientMapper {
  private RegisteredClientMapper() {}

  public static RegisteredClient toRegisteredClient(ClientConfiguration client, Instant now) {
    return toRegisteredClient(client, now, EndSessionMode.COMPATIBLE);
  }

  /** Maps an IS4 client row onto a SAS registered client, including every current secret. */
  public static RegisteredClient toRegisteredClient(
      ClientConfiguration client, Instant now, EndSessionMode mode) {
    RegisteredClient.Builder builder =
        RegisteredClient.withId(Integer.toString(client.id()))
            .clientId(client.clientId())
            .clientName(
                client.clientName() == null || client.clientName().isBlank()
                    ? client.clientId()
                    : client.clientName());

    if (client.requireClientSecret()) {
      builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
      builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
      String secrets = joinSecrets(client, now);
      if (!secrets.isEmpty()) {
        builder.clientSecret(secrets);
      }
    } else {
      builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
    }

    boolean refresh = client.allowOfflineAccess();
    boolean authorizationCode = false;
    for (String grant : client.grantTypes()) {
      if (grant == null) {
        continue;
      }
      switch (grant.toLowerCase(Locale.ROOT)) {
        case "authorization_code", "hybrid" -> {
          builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
          authorizationCode = true;
        }
        case "client_credentials" ->
            builder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
        case "refresh_token" -> {
          builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
          refresh = true;
        }
        case "delegation" ->
            builder.authorizationGrantType(DelegationGrantAuthenticationToken.DELEGATION);
        case "password" -> builder.authorizationGrantType(AuthorizationGrantType.PASSWORD);
        default -> {
          /* implicit and other legacy grants: not mapped */
        }
      }
    }
    if (refresh) {
      builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
    }

    for (String scope : client.scopes()) {
      if (scope != null && !scope.isBlank()) {
        builder.scope(scope);
      }
    }
    if (authorizationCode) {
      builder.scope("openid");
    }
    if (refresh) {
      builder.scope("offline_access");
    }
    for (String uri : client.redirectUris()) {
      if (uri != null && !uri.isBlank()) {
        builder.redirectUri(uri);
      }
    }
    for (String uri : client.postLogoutRedirectUris()) {
      if (uri != null && !uri.isBlank()) {
        builder.postLogoutRedirectUri(uri);
      }
    }

    int accessTtl = client.accessTokenLifetime() > 0 ? client.accessTokenLifetime() : 3600;
    int refreshTtl =
        client.absoluteRefreshTokenLifetime() > 0 ? client.absoluteRefreshTokenLifetime() : 2_592_000;
    builder.tokenSettings(
        TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofSeconds(accessTtl))
            .refreshTokenTimeToLive(Duration.ofSeconds(refreshTtl))
            .reuseRefreshTokens(true)
            .build());
    builder.clientSettings(
        ClientSettings.builder()
            .requireProofKey(client.requirePkce())
            .requireAuthorizationConsent(false)
            .build());
    RegisteredClient mapped = builder.build();
    if (mode != null && mode.isStrict()) {
      return withoutRefresh(mapped);
    }
    return mapped;
  }

  /**
   * Every non-expired SharedSecret, joined for {@link Is4ClientSecretPasswordEncoder}. The first
   * row is not special: callers may rotate secrets and keep the previous hash.
   */
  static String joinSecrets(ClientConfiguration client, Instant now) {
    if (client.secrets() == null) {
      return "";
    }
    return client.secrets().stream()
        .filter(s -> s != null && !s.isExpired(now))
        .map(ClientConfiguration.ClientSecretValue::value)
        .filter(v -> v != null && !v.isBlank())
        .collect(Collectors.joining(Is4ClientSecretPasswordEncoder.SEPARATOR));
  }

  /** Strict mode: access-token expiry ends the grant; the client must authenticate again. */
  static RegisteredClient withoutRefresh(RegisteredClient client) {
    return RegisteredClient.from(client)
        .authorizationGrantTypes(types -> types.remove(AuthorizationGrantType.REFRESH_TOKEN))
        .scopes(scopes -> scopes.remove("offline_access"))
        .build();
  }
}
