package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * IS4 extension grant {@code grant_type=delegation} with form field {@code token} (an access
 * token).
 */
public final class DelegationGrantAuthenticationConverter implements AuthenticationConverter {
  @Override
  @Nullable
  public Authentication convert(HttpServletRequest request) {
    String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
    if (!DelegationGrantAuthenticationToken.DELEGATION.getValue().equals(grantType)) {
      return null;
    }
    Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
    String token = request.getParameter("token");
    if (token == null || token.isBlank()) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "token is required", null));
    }
    Map<String, Object> additional = new HashMap<>();
    String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
    if (scope != null && !scope.isBlank()) {
      additional.put(OAuth2ParameterNames.SCOPE, scope);
    }
    return new DelegationGrantAuthenticationToken(clientPrincipal, token.trim(), additional);
  }
}
