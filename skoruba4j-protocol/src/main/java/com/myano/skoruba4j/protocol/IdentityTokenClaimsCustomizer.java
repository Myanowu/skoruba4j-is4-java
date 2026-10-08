package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    if (context.getPrincipal() != null
        && (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
            || "id_token".equals(context.getTokenType().getValue()))) {
      java.util.List<String> roles =
          IdentityClaims.roleNames(context.getPrincipal().getAuthorities());
      if (!roles.isEmpty()) {
        context.getClaims().claim("role", roles);
      }
    }
    if (!"id_token".equals(context.getTokenType().getValue())) {
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
}
