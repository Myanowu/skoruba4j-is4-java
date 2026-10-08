package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** Reads {@code grant_type=password} with {@code username} / {@code password}. */
public final class PasswordGrantAuthenticationConverter implements AuthenticationConverter {
  @Override
  @Nullable
  public Authentication convert(HttpServletRequest request) {
    String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
    if (!AuthorizationGrantType.PASSWORD.getValue().equals(grantType)) {
      return null;
    }
    Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
    String username = request.getParameter(OAuth2ParameterNames.USERNAME);
    String password = request.getParameter(OAuth2ParameterNames.PASSWORD);
    if (username == null || username.isBlank()) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "username is required", null));
    }
    if (password == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "password is required", null));
    }
    Map<String, Object> additional = new HashMap<>();
    String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
    if (scope != null && !scope.isBlank()) {
      additional.put(OAuth2ParameterNames.SCOPE, scope);
    }
    return new PasswordGrantAuthenticationToken(
        clientPrincipal, username.trim(), password, additional);
  }
}
