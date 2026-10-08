package com.myano.skoruba4j.domain.configstore;

import java.time.Instant;
import java.util.List;

/** Full ApiResources row + child collections (IS4 / Skoruba Admin shape). */
public record ApiResourceConfiguration(
    int id,
    String name,
    String displayName,
    String description,
    boolean enabled,
    boolean showInDiscoveryDocument,
    /** IS4 column: comma-separated algorithm names. */
    String allowedAccessTokenSigningAlgorithms,
    List<String> scopes,
    List<String> userClaims,
    List<Secret> secrets,
    List<Property> properties) {

  public record Secret(
      int id, String type, String description, Instant expiration, Instant created) {}

  public record Property(int id, String key, String value) {}

  public ApiResourceConfiguration {
    scopes = scopes == null ? List.of() : List.copyOf(scopes);
    userClaims = userClaims == null ? List.of() : List.copyOf(userClaims);
    secrets = secrets == null ? List.of() : List.copyOf(secrets);
    properties = properties == null ? List.of() : List.copyOf(properties);
  }
}
