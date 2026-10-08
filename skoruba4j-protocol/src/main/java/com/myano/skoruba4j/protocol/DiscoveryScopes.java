package com.myano.skoruba4j.protocol;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** OIDC discovery {@code scopes_supported}: always include OIDC basics, then DB names. */
public final class DiscoveryScopes {
  private DiscoveryScopes() {}

  public static List<String> merge(List<String> fromDatabase) {
    Set<String> scopes = new LinkedHashSet<>();
    scopes.add("openid");
    scopes.add("profile");
    scopes.add("email");
    scopes.add("offline_access");
    if (fromDatabase != null) {
      for (String name : fromDatabase) {
        if (name != null && !name.isBlank()) {
          scopes.add(name.trim());
        }
      }
    }
    return List.copyOf(scopes);
  }
}
