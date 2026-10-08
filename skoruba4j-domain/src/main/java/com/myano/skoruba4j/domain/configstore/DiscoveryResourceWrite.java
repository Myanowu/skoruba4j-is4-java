package com.myano.skoruba4j.domain.configstore;

import java.util.List;

/** Mutable fields for ApiScopes / IdentityResources create/update. */
public record DiscoveryResourceWrite(
    String name,
    String displayName,
    String description,
    boolean enabled,
    boolean showInDiscoveryDocument,
    boolean required,
    boolean emphasize,
    List<String> userClaims) {

  public DiscoveryResourceWrite {
    userClaims = userClaims == null ? List.of() : List.copyOf(userClaims);
  }
}
