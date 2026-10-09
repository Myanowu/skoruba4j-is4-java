package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.configstore.ResourceClaimTypesRepository;
import com.myano.skoruba4j.domain.identity.IdentityRole;
import com.myano.skoruba4j.domain.identity.RoleClaim;
import com.myano.skoruba4j.domain.identity.UserClaim;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code UserClaims} + {@code RoleClaims} and keeps only types requested by identity / API
 * resources for the granted scopes (IS4 profile claims).
 */
public final class Is4UserProfileClaims {
  private static final Set<String> RESERVED =
      Set.of(
          "sub",
          "iss",
          "aud",
          "exp",
          "iat",
          "nbf",
          "jti",
          "client_id",
          "scope",
          "amr",
          "idp",
          "azp",
          "auth_time",
          "nonce",
          "at_hash",
          "c_hash",
          "sid",
          "role",
          "roles");

  private Is4UserProfileClaims() {}

  /**
   * Identity-resource claim types for id_token / userinfo. When {@code
   * alwaysIncludeUserClaimsInIdToken} is false, callers should still expose these via userinfo.
   */
  public static Map<String, Object> forIdentityToken(
      JdbcRepositories jdbc, String userId, Collection<String> scopes) {
    Set<String> types = jdbc.resourceClaimTypes().identityClaimTypes(scopes);
    return filter(loadRaw(jdbc, userId), types);
  }

  /** ApiScope / ApiResource claim types for access tokens. */
  public static Map<String, Object> forAccessToken(
      JdbcRepositories jdbc, String userId, Collection<String> scopes) {
    Set<String> types = jdbc.resourceClaimTypes().apiClaimTypes(scopes);
    return filter(loadRaw(jdbc, userId), types);
  }

  static Map<String, List<String>> loadRaw(JdbcRepositories jdbc, String userId) {
    Map<String, List<String>> raw = new LinkedHashMap<>();
    if (jdbc == null || userId == null || userId.isBlank()) {
      return raw;
    }
    for (UserClaim claim : jdbc.users().listClaims(userId)) {
      add(raw, claim.type(), claim.value());
    }
    for (IdentityRole role : jdbc.users().listRoles(userId)) {
      for (RoleClaim claim : jdbc.roles().listClaims(role.id())) {
        add(raw, claim.type(), claim.value());
      }
    }
    return raw;
  }

  static Map<String, Object> filter(Map<String, List<String>> raw, Set<String> requestedTypes) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (raw == null || raw.isEmpty() || requestedTypes == null || requestedTypes.isEmpty()) {
      return out;
    }
    for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
      String type = entry.getKey();
      if (type == null || type.isBlank() || isReserved(type)) {
        continue;
      }
      if (!ResourceClaimTypesRepository.allows(requestedTypes, type)) {
        continue;
      }
      List<String> values = entry.getValue();
      if (values == null || values.isEmpty()) {
        continue;
      }
      if (values.size() == 1) {
        out.put(type, values.get(0));
      } else {
        out.put(type, List.copyOf(values));
      }
    }
    return out;
  }

  private static void add(Map<String, List<String>> raw, String type, String value) {
    if (type == null || type.isBlank()) {
      return;
    }
    String key = type.trim();
    raw.computeIfAbsent(key, ignored -> new ArrayList<>())
        .add(value == null ? "" : value);
  }

  static boolean isReserved(String claimType) {
    return RESERVED.contains(claimType.trim().toLowerCase(Locale.ROOT));
  }

  /** Distinct types present after load (tests). */
  static Set<String> typesOf(Map<String, List<String>> raw) {
    return raw == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(raw.keySet()));
  }
}
