package com.myano.skoruba4j.domain.configstore;

import java.util.List;

public record ClientWrite(
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
    List<String> claimLines,
    List<String> propertyLines) {

  public static ClientWrite basic(
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
      List<String> postLogoutRedirectUris) {
    return new ClientWrite(
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
        List.of());
  }
}
