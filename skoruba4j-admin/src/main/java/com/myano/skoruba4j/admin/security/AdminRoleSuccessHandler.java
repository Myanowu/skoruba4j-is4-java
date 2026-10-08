package com.myano.skoruba4j.admin.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

/**
 * Password or OIDC success requires the admin role. On success, return to the saved request (page
 * that triggered re-login) when present; otherwise {@code /admin}.
 */
public final class AdminRoleSuccessHandler implements AuthenticationSuccessHandler {
  private final String adminRole;
  private final SavedRequestAwareAuthenticationSuccessHandler success =
      new SavedRequestAwareAuthenticationSuccessHandler();

  public AdminRoleSuccessHandler(String adminRole) {
    this.adminRole = adminRole;
    success.setDefaultTargetUrl("/admin");
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    if (!AdminRole.hasRole(authentication.getAuthorities(), adminRole)) {
      String had =
          authentication.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .map(AdminRole::normalize)
              .filter(name -> !name.isEmpty())
              .distinct()
              .collect(Collectors.joining(","));
      new SecurityContextLogoutHandler().logout(request, response, authentication);
      String target =
          request.getContextPath()
              + "/login?denied=1&need="
              + enc(adminRole)
              + "&had="
              + enc(had);
      response.sendRedirect(target);
      return;
    }
    success.onAuthenticationSuccess(request, response, authentication);
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
