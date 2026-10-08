package com.myano.skoruba4j.domain.configstore;

import java.util.List;

/** Mutable fields for ApiResources create/update (children replaced on save). */
public record ApiResourceWrite(
    String name,
    String displayName,
    String description,
    boolean enabled,
    boolean showInDiscoveryDocument,
    String allowedAccessTokenSigningAlgorithms,
    List<String> scopes,
    List<String> userClaims) {

  public ApiResourceWrite {
    scopes = scopes == null ? List.of() : List.copyOf(scopes);
    userClaims = userClaims == null ? List.of() : List.copyOf(userClaims);
  }
}
