package com.myano.skoruba4j.protocol;

import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.util.StringUtils;

/**
 * IdentityServer4 access-token claim shapes that {@code IdentityServer4.AccessTokenValidation}
 * (4S WebAPI {@code ApiName}) expects. Spring Authorization Server defaults to JSON-array {@code
 * aud}/{@code scope}, which C# IS4 only uses when there are multiple values.
 */
public final class Is4AccessTokenClaims {
  private Is4AccessTokenClaims() {}

  public static void apply(JwtEncodingContext context, List<String> resourceNames) {
    if (context == null || !OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
      return;
    }
    Set<String> scopes = context.getAuthorizedScopes();
    if (scopes != null && !scopes.isEmpty()) {
      context.getClaims().claim(OAuth2ParameterNames.SCOPE, String.join(" ", scopes));
    }
    if (resourceNames != null && !resourceNames.isEmpty()) {
      if (resourceNames.size() == 1) {
        context.getClaims().claim(JwtClaimNames.AUD, resourceNames.get(0));
      } else {
        context.getClaims().audience(resourceNames);
      }
    }
    if (context.getRegisteredClient() != null
        && StringUtils.hasText(context.getRegisteredClient().getClientId())) {
      context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
    }
  }

  /** C# IS4 puts {@code idp=local} and {@code amr=["pwd"]} on interactive access tokens. */
  public static void applyLocalAuthenticationMethod(JwtEncodingContext context) {
    if (context == null || !OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
      return;
    }
    context.getClaims().claim("idp", "local");
    context.getClaims().claim("amr", List.of("pwd"));
  }

  /** IS4 JWT header {@code typ} is {@code JWT}, not SAS {@code at+jwt}. */
  public static void applyJwtType(JwtEncodingContext context) {
    if (context == null || context.getJwsHeader() == null) {
      return;
    }
    if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
        || OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
      context.getJwsHeader().type("JWT");
    }
  }
}
