package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

/**
 * Authenticates public clients ({@link ClientAuthenticationMethod#NONE}) on the token endpoint for
 * grants that are not PKCE authorization-code exchanges.
 *
 * <p>Spring Authorization Server's built-in {@code PublicClientAuthenticationConverter} requires
 * {@code code_verifier}, so {@code grant_type=password} (and similar) with only {@code client_id}
 * otherwise fails client authentication with HTTP 401 and an empty body.
 */
public final class Is4PublicClientAuthenticationConverter implements AuthenticationConverter {

  @Override
  @Nullable
  public Authentication convert(HttpServletRequest request) {
    String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
    if (!supportsGrant(grantType)) {
      return null;
    }
    String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
    if (!StringUtils.hasText(clientId)) {
      return null;
    }
    // Confidential clients: let ClientSecretPost / Basic converters handle them.
    if (StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))) {
      return null;
    }
    // PKCE public clients: leave to SAS PublicClientAuthenticationConverter.
    if (StringUtils.hasText(request.getParameter("code_verifier"))) {
      return null;
    }
    Map<String, Object> additional = new HashMap<>();
    additional.put(OAuth2ParameterNames.GRANT_TYPE, grantType);
    String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
    if (StringUtils.hasText(scope)) {
      additional.put(OAuth2ParameterNames.SCOPE, scope);
    }
    return new OAuth2ClientAuthenticationToken(
        clientId.trim(), ClientAuthenticationMethod.NONE, null, additional);
  }

  /**
   * Grants this converter and {@link Is4PublicClientAuthenticationProvider} may authenticate as
   * {@link ClientAuthenticationMethod#NONE} without PKCE. Authorization-code stays with SAS so
   * {@code code_verifier} is checked.
   */
  static boolean supportsGrant(String grantType) {
    if (!StringUtils.hasText(grantType)) {
      return false;
    }
    if (AuthorizationGrantType.PASSWORD.getValue().equals(grantType)) {
      return true;
    }
    if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
      return true;
    }
    return DelegationGrantAuthenticationToken.DELEGATION.getValue().equals(grantType);
  }
}
