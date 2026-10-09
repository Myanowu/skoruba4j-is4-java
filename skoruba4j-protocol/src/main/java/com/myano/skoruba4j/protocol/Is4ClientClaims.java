package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.configstore.ClientConfiguration.ClientClaim;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** IdentityServer4 client claims on access tokens ({@code ClientClaimsPrefix}, default {@code client_}). */
public final class Is4ClientClaims {
  private Is4ClientClaims() {}

  /**
   * Client-credentials tokens always carry client claims. Other grants include them only when
   * {@code AlwaysSendClientClaims} is set.
   */
  public static boolean includeOnAccessToken(
      boolean clientCredentials, boolean alwaysSendClientClaims) {
    return clientCredentials || alwaysSendClientClaims;
  }

  /**
   * Claim type is {@code prefix + type} when the prefix is present. Duplicate types stay a list so
   * JWT encodes them as an array ({@code client_role: ["Admin","MyRole"]}).
   */
  public static Map<String, List<String>> accessTokenClaims(
      String prefix, List<ClientClaim> claims) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    if (claims == null) {
      return out;
    }
    String applied = prefix == null || prefix.isBlank() ? "" : prefix;
    for (ClientClaim claim : claims) {
      if (claim == null || claim.type() == null || claim.type().isBlank()) {
        continue;
      }
      String name = applied + claim.type();
      out.computeIfAbsent(name, key -> new ArrayList<>())
          .add(claim.value() == null ? "" : claim.value());
    }
    return out;
  }
}
