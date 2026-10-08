package com.myano.skoruba4j.domain.configstore;

import java.util.List;

/**
 * Full ApiScopes / IdentityResources row + user claims and properties (IS4 / Skoruba Admin shape).
 */
public record DiscoveryResourceConfiguration(
    int id,
    String name,
    String displayName,
    String description,
    boolean enabled,
    boolean showInDiscoveryDocument,
    boolean required,
    boolean emphasize,
    List<String> userClaims,
    List<Property> properties) {

  public record Property(int id, String key, String value) {}

  public DiscoveryResourceConfiguration {
    userClaims = userClaims == null ? List.of() : List.copyOf(userClaims);
    properties = properties == null ? List.of() : List.copyOf(properties);
  }
}
