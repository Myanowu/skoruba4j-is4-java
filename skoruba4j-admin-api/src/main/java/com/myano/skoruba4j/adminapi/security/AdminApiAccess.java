package com.myano.skoruba4j.adminapi.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Admin API console / JSON API require the configured admin role (case-insensitive). */
public final class AdminApiAccess {
  private AdminApiAccess() {}

  public static AuthorizationManager<RequestAuthorizationContext> requireAdminRole(String role) {
    String required = UiOidcAuthorities.normalize(role);
    return (authentication, context) -> {
      Authentication current = authentication.get();
      return new AuthorizationDecision(ApiRoleSuccessHandler.hasRole(current, required));
    };
  }

  public static boolean hasAdminRole(Authentication authentication, String role) {
    return ApiRoleSuccessHandler.hasRole(authentication, role);
  }
}
