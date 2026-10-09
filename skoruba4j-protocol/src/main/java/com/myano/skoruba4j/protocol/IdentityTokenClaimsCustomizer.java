package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/** Adds profile/email claims to ID tokens. Access-token {@code aud} is handled separately. */
public final class IdentityTokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
  private final Optional<JdbcRepositories> jdbc;

  public IdentityTokenClaimsCustomizer(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void customize(JwtEncodingContext context) {
    boolean access = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
    boolean idToken = "id_token".equals(context.getTokenType().getValue());
    boolean clientCredentials =
        AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType());
    if (context.getPrincipal() != null && (access || idToken) && !clientCredentials) {
      List<String> roles = IdentityClaims.roleNames(context.getPrincipal().getAuthorities());
      if (!roles.isEmpty()) {
        context.getClaims().claim("role", roles);
      }
    }
    if (access) {
      addClientClaims(context, clientCredentials);
      if (!clientCredentials) {
        Is4AccessTokenClaims.applyLocalAuthenticationMethod(context);
      }
    }
    if (!idToken) {
      return;
    }
    String userId = context.getPrincipal() == null ? null : context.getPrincipal().getName();
    if (userId == null || userId.isBlank()) {
      return;
    }
    Set<String> scopes = context.getAuthorizedScopes();
    IdentityUser user = jdbc.flatMap(repos -> repos.users().findById(userId)).orElse(null);
    Map<String, Object> claims = IdentityClaims.claims(userId, user, scopes);
    claims.forEach(
        (name, value) -> {
          if (!IdTokenClaimNames.SUB.equals(name) && value != null) {
            context.getClaims().claim(name, value);
          }
        });
  }

  /** Copies {@code ClientClaims} onto the access token the way IdentityServer4 does. */
  private void addClientClaims(JwtEncodingContext context, boolean clientCredentials) {
    if (jdbc.isEmpty() || context.getRegisteredClient() == null) {
      return;
    }
    String clientId = context.getRegisteredClient().getClientId();
    jdbc
        .get()
        .clients()
        .findEnabledByClientId(clientId)
        .ifPresent(
            client -> {
              if (!Is4ClientClaims.includeOnAccessToken(
                  clientCredentials, client.alwaysSendClientClaims())) {
                return;
              }
              Is4ClientClaims.accessTokenClaims(client.clientClaimsPrefix(), client.claims())
                  .forEach(
                      (name, values) -> {
                        if (values.size() == 1) {
                          context.getClaims().claim(name, values.get(0));
                        } else if (!values.isEmpty()) {
                          context.getClaims().claim(name, values);
                        }
                      });
            });
  }
}
