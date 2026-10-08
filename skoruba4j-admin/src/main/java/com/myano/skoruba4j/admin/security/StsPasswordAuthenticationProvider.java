package com.myano.skoruba4j.admin.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

/** Form login that validates credentials via STS password grant (MobileWeb-style). */
public final class StsPasswordAuthenticationProvider implements AuthenticationProvider {
  private final StsPasswordLoginClient tokenClient;
  private final StsUserInfoClient userInfoClient;

  public StsPasswordAuthenticationProvider(
      StsPasswordLoginClient tokenClient, StsUserInfoClient userInfoClient) {
    this.tokenClient = tokenClient;
    this.userInfoClient = userInfoClient;
  }

  /**
   * Calls STS password grant, then builds an Admin session from token claims and UserInfo. Roles
   * come from JWT {@code role}/{@code roles}; Identity tables are not queried.
   */
  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String username = authentication.getName();
    Object credentials = authentication.getCredentials();
    String password = credentials == null ? "" : credentials.toString();
    if (username == null || username.isBlank() || password.isBlank()) {
      throw new BadCredentialsException("Invalid username or password");
    }
    OAuth2AccessTokenResponse tokens = tokenClient.requestToken(username.trim(), password);
    String accessToken =
        tokens.getAccessToken() == null ? null : tokens.getAccessToken().getTokenValue();
    String idToken = idTokenValue(tokens);
    Map<String, Object> accessClaims = JwtPayloads.parse(accessToken);
    Map<String, Object> idClaims = JwtPayloads.parse(idToken);
    Map<String, Object> userInfo =
        userInfoClient == null ? Map.of() : userInfoClient.fetch(accessToken);
    TokenIdentity identity = identityFromClaims(idClaims, accessClaims, userInfo);
    if (identity.subject() == null || identity.subject().isBlank()) {
      throw new BadCredentialsException("Invalid username or password");
    }
    List<GrantedAuthority> authorities =
        AdminOidcAuthorities.merge(
            List.of(), identity.roleClaim(), identity.rolesClaim(), identity.subject(), Optional.empty());
    StsSessionUser principal =
        new StsSessionUser(identity.subject(), idClaims, accessClaims, userInfo, authorities);
    return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }

  /** Unions {@code sub} / role claims from id_token and access_token (either may omit roles). */
  static TokenIdentity extractIdentity(OAuth2AccessTokenResponse response) {
    Map<String, Object> idClaims = JwtPayloads.parse(idTokenValue(response));
    Map<String, Object> accessClaims =
        response.getAccessToken() == null
            ? Map.of()
            : JwtPayloads.parse(response.getAccessToken().getTokenValue());
    return identityFromClaims(idClaims, accessClaims, Map.of());
  }

  static String subjectFromJwt(String jwt) {
    return identityFromJwt(jwt).subject();
  }

  /** Parses JWT payload claims needed for Admin role checks (no full JWT library). */
  static TokenIdentity identityFromJwt(String jwt) {
    return identityFromClaims(JwtPayloads.parse(jwt), Map.of(), Map.of());
  }

  static TokenIdentity identityFromClaims(
      Map<String, Object> idClaims,
      Map<String, Object> accessClaims,
      Map<String, Object> userInfo) {
    Map<String, Object> merged = new java.util.LinkedHashMap<>();
    if (accessClaims != null) {
      merged.putAll(accessClaims);
    }
    if (idClaims != null) {
      merged.putAll(idClaims);
    }
    if (userInfo != null) {
      merged.putAll(userInfo);
    }
    String subject = stringClaim(merged.get("sub"));
    List<String> roles = new ArrayList<>();
    addClaimRoles(roles, merged.get("role"));
    addClaimRoles(roles, merged.get("roles"));
    return new TokenIdentity(subject, roles.isEmpty() ? null : List.copyOf(roles), null);
  }

  private static String idTokenValue(OAuth2AccessTokenResponse response) {
    if (response.getAdditionalParameters() == null) {
      return null;
    }
    Object idToken = response.getAdditionalParameters().get("id_token");
    return idToken instanceof String s ? s : null;
  }

  private static void addClaimRoles(List<String> roles, Object claim) {
    if (claim instanceof Collection<?> values) {
      for (Object value : values) {
        if (value != null && !value.toString().isBlank()) {
          roles.add(value.toString());
        }
      }
    } else if (claim instanceof String value && !value.isBlank()) {
      roles.add(value);
    }
  }

  private static String stringClaim(Object value) {
    if (value instanceof String text && !text.isBlank()) {
      return text;
    }
    return value == null ? null : value.toString();
  }

  record TokenIdentity(String subject, Object roleClaim, Object rolesClaim) {
    static TokenIdentity empty() {
      return new TokenIdentity(null, null, null);
    }
  }
}
