package com.myano.skoruba4j.domain.configstore;

import java.time.Instant;
import java.util.List;

public record ClientConfiguration(
    int id,
    String clientId,
    String clientName,
    boolean enabled,
    boolean requireClientSecret,
    boolean requirePkce,
    boolean allowOfflineAccess,
    boolean requireConsent,
    boolean alwaysIncludeUserClaimsInIdToken,
    int identityTokenLifetime,
    int accessTokenLifetime,
    int authorizationCodeLifetime,
    int absoluteRefreshTokenLifetime,
    int slidingRefreshTokenLifetime,
    int accessTokenType,
    String frontChannelLogoutUri,
    String backChannelLogoutUri,
    List<String> grantTypes,
    List<String> scopes,
    List<String> redirectUris,
    List<String> postLogoutRedirectUris,
    List<String> corsOrigins,
    List<ClientClaim> claims,
    List<ClientProperty> properties,
    List<ClientSecretValue> secrets) {

  public record ClientSecretValue(
      int id,
      String value,
      String type,
      Instant expiration,
      String description,
      Instant created) {
    public ClientSecretValue(int id, String value, String type, Instant expiration) {
      this(id, value, type, expiration, null, null);
    }

    public boolean isExpired(Instant now) {
      return expiration != null && !expiration.isAfter(now);
    }
  }

  public record ClientClaim(int id, String type, String value) {}

  public record ClientProperty(int id, String key, String value) {}

  public static ClientConfiguration protocolSample(
      int id,
      String clientId,
      String clientName,
      boolean enabled,
      boolean requireClientSecret,
      boolean requirePkce,
      boolean allowOfflineAccess,
      int accessTokenLifetime,
      int absoluteRefreshTokenLifetime,
      List<String> grantTypes,
      List<String> scopes,
      List<String> redirectUris,
      List<String> postLogoutRedirectUris,
      List<ClientSecretValue> secrets) {
    return new ClientConfiguration(
        id,
        clientId,
        clientName,
        enabled,
        requireClientSecret,
        requirePkce,
        allowOfflineAccess,
        false,
        false,
        300,
        accessTokenLifetime,
        300,
        absoluteRefreshTokenLifetime,
        1_296_000,
        0,
        "",
        "",
        grantTypes,
        scopes,
        redirectUris,
        postLogoutRedirectUris,
        List.of(),
        List.of(),
        List.of(),
        secrets);
  }
}
