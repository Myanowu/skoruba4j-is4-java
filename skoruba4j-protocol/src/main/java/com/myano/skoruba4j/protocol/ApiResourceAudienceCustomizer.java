package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.configstore.ApiAudienceRepository;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/** Sets JWT {@code aud} to {@code ApiResources.Name} via {@code ApiResourceScopes}. */
public final class ApiResourceAudienceCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
  private final ApiAudienceRepository audiences;

  public ApiResourceAudienceCustomizer(ApiAudienceRepository audiences) {
    this.audiences = audiences;
  }

  @Override
  public void customize(JwtEncodingContext context) {
    if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType()) || audiences == null) {
      return;
    }
    Set<String> scopes = context.getAuthorizedScopes();
    List<String> resourceNames = audiences.resourceNamesForScopes(scopes);
    if (!resourceNames.isEmpty()) {
      context.getClaims().audience(resourceNames);
    }
  }
}
