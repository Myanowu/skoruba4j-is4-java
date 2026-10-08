package com.myano.skoruba4j.admin.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Admin session after STS password grant: profile comes from tokens and {@code /connect/userinfo},
 * not from Identity tables. {@link Serializable} for HttpSession storage.
 */
public final class StsSessionUser implements UserDetails, OAuth2User, Serializable {
  @Serial private static final long serialVersionUID = 1L;

  private final String subject;
  private final Map<String, Object> claims;
  private final Map<String, Object> idTokenClaims;
  private final Map<String, Object> accessTokenClaims;
  private final Map<String, Object> userInfo;
  private final List<GrantedAuthority> authorities;

  public StsSessionUser(
      String subject,
      Map<String, Object> idTokenClaims,
      Map<String, Object> accessTokenClaims,
      Map<String, Object> userInfo,
      Collection<? extends GrantedAuthority> authorities) {
    this.subject = subject == null ? "" : subject;
    this.idTokenClaims = copy(idTokenClaims);
    this.accessTokenClaims = copy(accessTokenClaims);
    this.userInfo = copy(userInfo);
    this.claims = merge(this.accessTokenClaims, this.idTokenClaims, this.userInfo);
    this.authorities = authorities == null ? List.of() : List.copyOf(authorities);
  }

  /** Identity {@code Users.Id} from token {@code sub}. */
  public String subject() {
    return subject;
  }

  /** Parsed id_token payload (empty when STS omitted id_token). */
  public Map<String, Object> idTokenClaims() {
    return idTokenClaims;
  }

  /** Parsed access_token payload. */
  public Map<String, Object> accessTokenClaims() {
    return accessTokenClaims;
  }

  /** STS {@code /connect/userinfo} body. */
  public Map<String, Object> userInfo() {
    return userInfo;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return claims;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return "";
  }

  /** Screen name: {@code preferred_username}, then {@code name}, else {@code sub}. */
  @Override
  public String getUsername() {
    return displayName();
  }

  @Override
  public String getName() {
    return displayName();
  }

  private String displayName() {
    Object username = claims.get("preferred_username");
    if (username instanceof String text && !text.isBlank()) {
      return text;
    }
    Object name = claims.get("name");
    if (name instanceof String text && !text.isBlank()) {
      return text;
    }
    return subject;
  }

  private static Map<String, Object> copy(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        copy.put(entry.getKey(), entry.getValue());
      }
    }
    return Collections.unmodifiableMap(copy);
  }

  @SafeVarargs
  private static Map<String, Object> merge(Map<String, Object>... layers) {
    LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
    for (Map<String, Object> layer : layers) {
      if (layer == null) {
        continue;
      }
      for (Map.Entry<String, Object> entry : layer.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          merged.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return Collections.unmodifiableMap(merged);
  }
}
