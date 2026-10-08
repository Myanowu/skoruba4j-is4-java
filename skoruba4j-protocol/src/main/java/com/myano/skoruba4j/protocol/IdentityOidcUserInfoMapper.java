package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;

public final class IdentityOidcUserInfoMapper
    implements Function<OidcUserInfoAuthenticationContext, OidcUserInfo> {
  private final Optional<JdbcRepositories> jdbc;

  public IdentityOidcUserInfoMapper(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public OidcUserInfo apply(OidcUserInfoAuthenticationContext context) {
    OAuth2Authorization authorization = context.getAuthorization();
    String userId = authorization.getPrincipalName();
    Set<String> scopes = authorization.getAuthorizedScopes();
    IdentityUser user =
        jdbc.flatMap(repos -> repos.users().findById(userId)).orElse(null);
    return IdentityClaims.userInfo(userId, user, scopes);
  }
}
