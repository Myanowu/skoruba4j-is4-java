package com.myano.skoruba4j.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myano.skoruba4j.domain.configstore.PersistedGrantRecord;
import com.myano.skoruba4j.domain.configstore.PersistedGrantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Maps Spring Authorization Server authorizations onto IS4 {@code PersistedGrants} (same table C#
 * {@code PersistedGrantStore} uses).
 *
 * <p>Primary row: {@code Key}=authorization id, {@code Type}=authorization, {@code Data}=JSON
 * snapshot. Token index rows: {@code Key}=SHA-256(token) (IS4-style), {@code Type}=authorization_code
 * / refresh_token / …, {@code Data}=authorization id.
 */
public final class PersistedGrantOAuth2AuthorizationService implements OAuth2AuthorizationService {
  private static final String TYPE_AUTH_REQUEST = "oauth2_authorization_request";
  private static final String TYPE_AUTHENTICATION = "authentication";
  /** SAS {@code Token.isBeforeUse()} casts these claim values to {@link Instant}. */
  private static final Set<String> INSTANT_CLAIM_NAMES =
      Set.of("nbf", "iat", "exp", "auth_time", "updated_at");

  private final PersistedGrantRepository grants;
  private final RegisteredClientRepository clients;
  private final ObjectMapper objectMapper;

  public PersistedGrantOAuth2AuthorizationService(
      PersistedGrantRepository grants, RegisteredClientRepository clients) {
    Assert.notNull(grants, "grants cannot be null");
    Assert.notNull(clients, "clients cannot be null");
    this.grants = grants;
    this.clients = clients;
    // Plain mapper only — SecurityJackson2Modules default-typing breaks Map round-trips
    // and caused UserInfo "cannot parse authorization payload".
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void save(OAuth2Authorization authorization) {
    Assert.notNull(authorization, "authorization cannot be null");
    grants.deleteAuthorizationBundle(authorization.getId());
    String clientId = resolveClientId(authorization.getRegisteredClientId());
    String subjectId = authorization.getPrincipalName();
    String sessionId = stringAttr(authorization, "sid");
    Instant created = Instant.now();
    Instant expires = farthestExpiry(authorization);

    Map<String, Object> payload = toPayload(authorization);
    String json = writeJson(payload);
    grants.upsert(
        authorization.getId(),
        PersistedGrantRepository.TYPE_AUTHORIZATION,
        subjectId,
        clientId,
        sessionId,
        authorization.getAuthorizationGrantType() == null
            ? null
            : authorization.getAuthorizationGrantType().getValue(),
        json,
        created,
        expires,
        null);

    indexToken(
        authorization,
        clientId,
        subjectId,
        sessionId,
        OAuth2ParameterNames.CODE,
        "authorization_code",
        authorization.getToken(OAuth2AuthorizationCode.class));
    indexToken(
        authorization,
        clientId,
        subjectId,
        sessionId,
        OAuth2TokenType.REFRESH_TOKEN.getValue(),
        "refresh_token",
        authorization.getRefreshToken());
    // Must index JWTs too: SAS UserInfo / introspect call findByToken(access_token).
    indexToken(
        authorization,
        clientId,
        subjectId,
        sessionId,
        OAuth2TokenType.ACCESS_TOKEN.getValue(),
        PersistedGrantRepository.TYPE_ACCESS_TOKEN,
        authorization.getAccessToken());
    indexToken(
        authorization,
        clientId,
        subjectId,
        sessionId,
        OAuth2ParameterNames.USER_CODE,
        "user_code",
        authorization.getToken(OAuth2UserCode.class));
    indexToken(
        authorization,
        clientId,
        subjectId,
        sessionId,
        OAuth2ParameterNames.DEVICE_CODE,
        "device_code",
        authorization.getToken(OAuth2DeviceCode.class));
  }

  @Override
  public void remove(OAuth2Authorization authorization) {
    Assert.notNull(authorization, "authorization cannot be null");
    grants.deleteAuthorizationBundle(authorization.getId());
  }

  @Override
  @Nullable
  public OAuth2Authorization findById(String id) {
    Assert.hasText(id, "id cannot be empty");
    return grants
        .findByKey(id)
        .filter(row -> PersistedGrantRepository.TYPE_AUTHORIZATION.equals(row.type()))
        .map(this::fromRecord)
        .orElse(null);
  }

  @Override
  @Nullable
  public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
    Assert.hasText(token, "token cannot be empty");
    String hashed = hashKey(token);
    PersistedGrantRecord index = grants.findByKey(hashed).orElse(null);
    if (index == null) {
      index = grants.findByKey(token).orElse(null);
    }
    if (index == null) {
      return null;
    }
    String authId =
        PersistedGrantRepository.TYPE_AUTHORIZATION.equals(index.type())
            ? index.key()
            : index.data();
    if (authId == null || authId.isBlank()) {
      return null;
    }
    return findById(authId);
  }

  private void indexToken(
      OAuth2Authorization authorization,
      String clientId,
      String subjectId,
      String sessionId,
      String ignoredTypeHint,
      String persistedType,
      OAuth2Authorization.Token<?> tokenHolder) {
    if (tokenHolder == null || tokenHolder.getToken() == null) {
      return;
    }
    String value = tokenHolder.getToken().getTokenValue();
    if (!StringUtils.hasText(value)) {
      return;
    }
    Instant issued = tokenHolder.getToken().getIssuedAt();
    Instant exp = tokenHolder.getToken().getExpiresAt();
    grants.upsert(
        hashKey(value),
        persistedType,
        subjectId,
        clientId,
        sessionId,
        null,
        authorization.getId(),
        issued == null ? Instant.now() : issued,
        exp,
        null);
  }

  private Map<String, Object> toPayload(OAuth2Authorization authorization) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", authorization.getId());
    map.put("registeredClientId", authorization.getRegisteredClientId());
    map.put("principalName", authorization.getPrincipalName());
    map.put(
        "authorizationGrantType",
        authorization.getAuthorizationGrantType() == null
            ? null
            : authorization.getAuthorizationGrantType().getValue());
    map.put(
        "authorizedScopes",
        authorization.getAuthorizedScopes() == null
            ? ""
            : StringUtils.collectionToCommaDelimitedString(authorization.getAuthorizedScopes()));
    map.put("attributes", sanitizeValue(authorization.getAttributes()));
    Object state = authorization.getAttribute(OAuth2ParameterNames.STATE);
    if (state != null) {
      map.put("state", String.valueOf(state));
    }
    putToken(map, "authorizationCode", authorization.getToken(OAuth2AuthorizationCode.class));
    putToken(map, "accessToken", authorization.getAccessToken());
    putToken(map, "oidcIdToken", authorization.getToken(OidcIdToken.class));
    putToken(map, "refreshToken", authorization.getRefreshToken());
    putToken(map, "userCode", authorization.getToken(OAuth2UserCode.class));
    putToken(map, "deviceCode", authorization.getToken(OAuth2DeviceCode.class));
    if (authorization.getAccessToken() != null
        && authorization.getAccessToken().getToken() != null) {
      OAuth2AccessToken access = authorization.getAccessToken().getToken();
      map.put(
          "accessTokenType",
          access.getTokenType() == null ? null : access.getTokenType().getValue());
      map.put(
          "accessTokenScopes",
          access.getScopes() == null
              ? ""
              : StringUtils.collectionToCommaDelimitedString(access.getScopes()));
    }
    return map;
  }

  private void putToken(Map<String, Object> map, String prefix, OAuth2Authorization.Token<?> token) {
    if (token == null || token.getToken() == null) {
      return;
    }
    map.put(prefix + "Value", token.getToken().getTokenValue());
    Instant issued = token.getToken().getIssuedAt();
    Instant exp = token.getToken().getExpiresAt();
    map.put(prefix + "IssuedAt", issued == null ? null : issued.toString());
    map.put(prefix + "ExpiresAt", exp == null ? null : exp.toString());
    map.put(prefix + "Metadata", sanitizeValue(token.getMetadata()));
  }

  @SuppressWarnings("unchecked")
  private OAuth2Authorization fromRecord(PersistedGrantRecord row) {
    Map<String, Object> payload = readMap(row.data());
    String registeredClientId = stringVal(payload.get("registeredClientId"));
    RegisteredClient client = clients.findById(registeredClientId);
    if (client == null) {
      // Is4RegisteredClientRepository uses ClientId as RegisteredClient.id
      client = clients.findByClientId(registeredClientId);
    }
    if (client == null) {
      throw new IllegalStateException(
          "RegisteredClient not found for PersistedGrant " + row.key() + ": " + registeredClientId);
    }
    OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client);
    builder.id(stringVal(payload.get("id")));
    builder.principalName(stringVal(payload.get("principalName")));
    String grantType = stringVal(payload.get("authorizationGrantType"));
    if (StringUtils.hasText(grantType)) {
      builder.authorizationGrantType(new AuthorizationGrantType(grantType));
    }
    Set<String> scopes =
        StringUtils.commaDelimitedListToSet(stringVal(payload.get("authorizedScopes")));
    builder.authorizedScopes(scopes);
    Map<String, Object> attributes = hydrateAttributes(asMap(payload.get("attributes")));
    builder.attributes(attrs -> attrs.putAll(attributes));
    String state = stringVal(payload.get("state"));
    if (StringUtils.hasText(state)) {
      builder.attribute(OAuth2ParameterNames.STATE, state);
    }

    String codeValue = stringVal(payload.get("authorizationCodeValue"));
    if (StringUtils.hasText(codeValue)) {
      Instant issued = parseInstant(payload.get("authorizationCodeIssuedAt"));
      Instant exp = parseInstant(payload.get("authorizationCodeExpiresAt"));
      Map<String, Object> meta = hydrateTokenMetadata(asMap(payload.get("authorizationCodeMetadata")));
      OAuth2AuthorizationCode code = new OAuth2AuthorizationCode(codeValue, issued, exp);
      builder.token(code, m -> m.putAll(meta));
    }

    String accessValue = stringVal(payload.get("accessTokenValue"));
    if (StringUtils.hasText(accessValue)) {
      Instant issued = parseInstant(payload.get("accessTokenIssuedAt"));
      Instant exp = parseInstant(payload.get("accessTokenExpiresAt"));
      Map<String, Object> meta = hydrateTokenMetadata(asMap(payload.get("accessTokenMetadata")));
      OAuth2AccessToken.TokenType tokenType = OAuth2AccessToken.TokenType.BEARER;
      String type = stringVal(payload.get("accessTokenType"));
      if (OAuth2AccessToken.TokenType.DPOP.getValue().equalsIgnoreCase(type)) {
        tokenType = OAuth2AccessToken.TokenType.DPOP;
      }
      Set<String> accessScopes =
          StringUtils.commaDelimitedListToSet(stringVal(payload.get("accessTokenScopes")));
      OAuth2AccessToken access =
          new OAuth2AccessToken(tokenType, accessValue, issued, exp, accessScopes);
      builder.token(access, m -> m.putAll(meta));
    }

    String idTokenValue = stringVal(payload.get("oidcIdTokenValue"));
    if (StringUtils.hasText(idTokenValue)) {
      Instant issued = parseInstant(payload.get("oidcIdTokenIssuedAt"));
      Instant exp = parseInstant(payload.get("oidcIdTokenExpiresAt"));
      Map<String, Object> meta = hydrateTokenMetadata(asMap(payload.get("oidcIdTokenMetadata")));
      Map<String, Object> claims = asMap(meta.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME));
      if (claims.isEmpty()) {
        claims = new HashMap<>();
        claims.put("sub", stringVal(payload.get("principalName")));
        meta.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims);
      }
      OidcIdToken idToken = new OidcIdToken(idTokenValue, issued, exp, claims);
      builder.token(idToken, m -> m.putAll(meta));
    }

    String refreshValue = stringVal(payload.get("refreshTokenValue"));
    if (StringUtils.hasText(refreshValue)) {
      Instant issued = parseInstant(payload.get("refreshTokenIssuedAt"));
      Instant exp = parseInstant(payload.get("refreshTokenExpiresAt"));
      Map<String, Object> meta = hydrateTokenMetadata(asMap(payload.get("refreshTokenMetadata")));
      OAuth2RefreshToken refresh = new OAuth2RefreshToken(refreshValue, issued, exp);
      builder.token(refresh, m -> m.putAll(meta));
    }

    String userCodeValue = stringVal(payload.get("userCodeValue"));
    if (StringUtils.hasText(userCodeValue)) {
      Instant issued = parseInstant(payload.get("userCodeIssuedAt"));
      Instant exp = parseInstant(payload.get("userCodeExpiresAt"));
      Map<String, Object> meta = hydrateTokenMetadata(asMap(payload.get("userCodeMetadata")));
      OAuth2UserCode userCode = new OAuth2UserCode(userCodeValue, issued, exp);
      builder.token(userCode, m -> m.putAll(meta));
    }

    String deviceCodeValue = stringVal(payload.get("deviceCodeValue"));
    if (StringUtils.hasText(deviceCodeValue)) {
      Instant issued = parseInstant(payload.get("deviceCodeIssuedAt"));
      Instant exp = parseInstant(payload.get("deviceCodeExpiresAt"));
      Map<String, Object> meta = hydrateTokenMetadata(asMap(payload.get("deviceCodeMetadata")));
      OAuth2DeviceCode deviceCode = new OAuth2DeviceCode(deviceCodeValue, issued, exp);
      builder.token(deviceCode, m -> m.putAll(meta));
    }

    return builder.build();
  }

  /**
   * Keep JSON-safe values. Persist {@link OAuth2AuthorizationRequest} (PKCE) and a minimal {@link
   * Authentication}; drop other Security types that break plain Jackson round-trips.
   */
  @SuppressWarnings("unchecked")
  private static Object sanitizeValue(Object value) {
    if (value == null
        || value instanceof String
        || value instanceof Number
        || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Instant instant) {
      return instant.toString();
    }
    if (value instanceof OAuth2AuthorizationRequest authorizationRequest) {
      return serializeAuthorizationRequest(authorizationRequest);
    }
    if (value instanceof Authentication authentication) {
      return serializeAuthentication(authentication);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() == null) {
          continue;
        }
        Object sanitized = sanitizeValue(entry.getValue());
        if (sanitized != null || entry.getValue() == null) {
          out.put(String.valueOf(entry.getKey()), sanitized);
        }
      }
      return out;
    }
    if (value instanceof Collection<?> collection) {
      List<Object> out = new ArrayList<>();
      for (Object item : collection) {
        Object sanitized = sanitizeValue(item);
        if (sanitized != null || item == null) {
          out.add(sanitized);
        }
      }
      return out;
    }
    return null;
  }

  private static Map<String, Object> serializeAuthorizationRequest(
      OAuth2AuthorizationRequest request) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("@type", TYPE_AUTH_REQUEST);
    out.put("authorizationUri", request.getAuthorizationUri());
    out.put("clientId", request.getClientId());
    out.put("redirectUri", request.getRedirectUri());
    out.put(
        "scopes",
        request.getScopes() == null
            ? List.of()
            : new ArrayList<>(request.getScopes()));
    out.put("state", request.getState());
    out.put(
        "additionalParameters",
        sanitizeValue(
            request.getAdditionalParameters() == null
                ? Map.of()
                : request.getAdditionalParameters()));
    out.put(
        "attributes",
        sanitizeValue(request.getAttributes() == null ? Map.of() : request.getAttributes()));
    return out;
  }

  private static Map<String, Object> serializeAuthentication(Authentication authentication) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("@type", TYPE_AUTHENTICATION);
    out.put("name", authentication.getName());
    out.put("authenticated", authentication.isAuthenticated());
    List<String> authorities = new ArrayList<>();
    if (authentication.getAuthorities() != null) {
      for (GrantedAuthority authority : authentication.getAuthorities()) {
        if (authority != null && StringUtils.hasText(authority.getAuthority())) {
          authorities.add(authority.getAuthority());
        }
      }
    }
    out.put("authorities", authorities);
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> hydrateAttributes(Map<String, Object> raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      out.put(entry.getKey(), hydrateValue(entry.getValue()));
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Object hydrateValue(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      if (value instanceof Collection<?> collection) {
        List<Object> out = new ArrayList<>();
        for (Object item : collection) {
          out.add(hydrateValue(item));
        }
        return out;
      }
      return value;
    }
    Map<String, Object> typed = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() != null) {
        typed.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    String type = stringVal(typed.get("@type"));
    if (TYPE_AUTH_REQUEST.equals(type)) {
      return deserializeAuthorizationRequest(typed);
    }
    if (TYPE_AUTHENTICATION.equals(type)) {
      return deserializeAuthentication(typed);
    }
    Map<String, Object> nested = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : typed.entrySet()) {
      nested.put(entry.getKey(), hydrateValue(entry.getValue()));
    }
    return nested;
  }

  @SuppressWarnings("unchecked")
  private static OAuth2AuthorizationRequest deserializeAuthorizationRequest(
      Map<String, Object> raw) {
    Set<String> scopes = new LinkedHashSet<>();
    Object scopesRaw = raw.get("scopes");
    if (scopesRaw instanceof Collection<?> collection) {
      for (Object scope : collection) {
        if (scope != null && StringUtils.hasText(String.valueOf(scope))) {
          scopes.add(String.valueOf(scope));
        }
      }
    }
    Map<String, Object> additional = asMapStatic(raw.get("additionalParameters"));
    Map<String, Object> attributes = asMapStatic(raw.get("attributes"));
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri(stringVal(raw.get("authorizationUri")))
        .clientId(stringVal(raw.get("clientId")))
        .redirectUri(stringVal(raw.get("redirectUri")))
        .scopes(scopes)
        .state(stringVal(raw.get("state")))
        .additionalParameters(additional)
        .attributes(attributes)
        .build();
  }

  private static Authentication deserializeAuthentication(Map<String, Object> raw) {
    String name = stringVal(raw.get("name"));
    List<GrantedAuthority> authorities = new ArrayList<>();
    Object authoritiesRaw = raw.get("authorities");
    if (authoritiesRaw instanceof Collection<?> collection) {
      for (Object authority : collection) {
        if (authority != null && StringUtils.hasText(String.valueOf(authority))) {
          authorities.add(new SimpleGrantedAuthority(String.valueOf(authority)));
        }
      }
    }
    UsernamePasswordAuthenticationToken token =
        UsernamePasswordAuthenticationToken.authenticated(name, "N/A", authorities);
    if (!Boolean.TRUE.equals(raw.get("authenticated"))) {
      return UsernamePasswordAuthenticationToken.unauthenticated(name, "N/A");
    }
    return token;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMapStatic(Object raw) {
    if (raw == null) {
      return new LinkedHashMap<>();
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() != null) {
          out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
      return out;
    }
    return new LinkedHashMap<>();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object raw) {
    if (raw == null) {
      return new HashMap<>();
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> out = new HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() != null) {
          out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
      return out;
    }
    if (raw instanceof String text) {
      return readMap(text);
    }
    return new HashMap<>();
  }

  private String resolveClientId(String registeredClientId) {
    RegisteredClient client = clients.findById(registeredClientId);
    if (client == null) {
      client = clients.findByClientId(registeredClientId);
    }
    return client == null ? registeredClientId : client.getClientId();
  }

  private static Instant farthestExpiry(OAuth2Authorization authorization) {
    Instant best = null;
    best = later(best, tokenExpiry(authorization.getRefreshToken()));
    best = later(best, tokenExpiry(authorization.getAccessToken()));
    best = later(best, tokenExpiry(authorization.getToken(OAuth2AuthorizationCode.class)));
    best = later(best, tokenExpiry(authorization.getToken(OidcIdToken.class)));
    return best;
  }

  private static Instant tokenExpiry(OAuth2Authorization.Token<?> token) {
    if (token == null || token.getToken() == null) {
      return null;
    }
    return token.getToken().getExpiresAt();
  }

  private static Instant later(Instant a, Instant b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.isAfter(b) ? a : b;
  }

  private static String stringAttr(OAuth2Authorization authorization, String name) {
    Object value = authorization.getAttribute(name);
    return value == null ? null : String.valueOf(value);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception e) {
      throw new IllegalArgumentException("cannot serialize authorization payload", e);
    }
  }

  private Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) {
      return new HashMap<>();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("cannot parse authorization payload", e);
    }
  }

  private static String stringVal(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static Instant parseInstant(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof Number number) {
      return Instant.ofEpochSecond(number.longValue());
    }
    String text = String.valueOf(value);
    if (text.isBlank() || "null".equals(text)) {
      return null;
    }
    try {
      return Instant.parse(text);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Restore Instant-typed claim values. Plain Jackson stores them as ISO-8601 strings; SAS {@code
   * OAuth2Authorization.Token#isBeforeUse()} does {@code (Instant) claims.get("nbf")}.
   */
  private static Map<String, Object> hydrateTokenMetadata(Map<String, Object> meta) {
    if (meta == null || meta.isEmpty()) {
      return meta == null ? new HashMap<>() : meta;
    }
    Map<String, Object> out = new LinkedHashMap<>(meta);
    Object claimsRaw = meta.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME);
    if (claimsRaw instanceof Map<?, ?>) {
      out.put(
          OAuth2Authorization.Token.CLAIMS_METADATA_NAME, hydrateClaims(asMapStatic(claimsRaw)));
    }
    return out;
  }

  private static Map<String, Object> hydrateClaims(Map<String, Object> claims) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : claims.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (INSTANT_CLAIM_NAMES.contains(key)) {
        Instant instant = parseInstant(value);
        out.put(key, instant != null ? instant : value);
      } else {
        out.put(key, value);
      }
    }
    return out;
  }

  /** IS4-style grant key: Base64(SHA-256(UTF-8 token)). */
  static String hashKey(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));
    }
  }
}
