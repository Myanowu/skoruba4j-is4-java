package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.domain.configstore.AuditLogWriter;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Lightweight write-side for Skoruba {@code AuditLog}: records successful mutating Admin requests
 * so the Audit Log page is not empty on a fresh Java install.
 */
@Component
public class AdminAuditLogInterceptor implements HandlerInterceptor {
  private final Optional<JdbcRepositories> jdbc;

  public AdminAuditLogInterceptor(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception ex) {
    if (ex != null || !shouldRecord(request, response)) {
      return;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || auth instanceof AnonymousAuthenticationToken) {
      return;
    }
    AuditLogWriter.adminRequest(
        jdbc, auth.getName(), request.getMethod() + " " + request.getRequestURI());
  }

  private static boolean shouldRecord(HttpServletRequest request, HttpServletResponse response) {
    String method = request.getMethod();
    if (!"POST".equalsIgnoreCase(method)
        && !"PUT".equalsIgnoreCase(method)
        && !"DELETE".equalsIgnoreCase(method)
        && !"PATCH".equalsIgnoreCase(method)) {
      return false;
    }
    String path = request.getRequestURI();
    if (path == null || !path.startsWith("/admin/")) {
      return false;
    }
    if (path.startsWith("/admin/audit-logs")) {
      return false;
    }
    int status = response.getStatus();
    return status >= 200 && status < 400;
  }
}
