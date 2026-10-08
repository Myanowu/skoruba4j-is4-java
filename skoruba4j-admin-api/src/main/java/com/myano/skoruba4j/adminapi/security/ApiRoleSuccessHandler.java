package com.myano.skoruba4j.adminapi.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

/** Password or OIDC success requires the configured admin role. */
public final class ApiRoleSuccessHandler implements AuthenticationSuccessHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiRoleSuccessHandler.class);

  private final String adminRole;
  private final SimpleUrlAuthenticationSuccessHandler success =
      new SimpleUrlAuthenticationSuccessHandler("/");

  public ApiRoleSuccessHandler(String adminRole) {
    this.adminRole = adminRole;
    success.setAlwaysUseDefaultTargetUrl(true);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    if (!hasRole(authentication, adminRole)) {
      log.info(
          "admin-api sign-in rejected: principal={} needs role={}",
          authentication == null ? null : authentication.getName(),
          adminRole);
      new SecurityContextLogoutHandler().logout(request, response, authentication);
      response.sendRedirect(request.getContextPath() + "/login?denied=1");
      return;
    }
    success.onAuthenticationSuccess(request, response, authentication);
  }

  static boolean hasRole(Authentication authentication, String role) {
    String required = UiOidcAuthorities.normalize(role);
    if (required.isEmpty() || authentication == null) {
      return false;
    }
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if (authority != null
          && required.equalsIgnoreCase(UiOidcAuthorities.normalize(authority.getAuthority()))) {
        return true;
      }
    }
    return false;
  }
}
