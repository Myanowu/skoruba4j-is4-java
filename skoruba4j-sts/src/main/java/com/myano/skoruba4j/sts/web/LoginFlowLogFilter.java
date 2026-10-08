package com.myano.skoruba4j.sts.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Logs login and authorize so a bounce back to /login is visible in skoruba4j-sts.log. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LoginFlowLogFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(LoginFlowLogFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI() == null ? "" : request.getRequestURI();
    boolean watch =
        "/login".equals(path)
            || path.startsWith("/connect/authorize")
            || path.startsWith("/oauth2/")
            || path.startsWith("/login/oauth2/");
    filterChain.doFilter(request, response);
    if (!watch) {
      return;
    }
    HttpSession session = request.getSession(false);
    Object stored = session == null ? null : session.getAttribute("SPRING_SECURITY_CONTEXT");
    String who = stored == null ? "anonymous" : stored.getClass().getSimpleName();
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
      who = auth.getClass().getSimpleName();
    }
    log.info(
        "login-flow {} {} -> {} auth={} session={}",
        request.getMethod(),
        path,
        response.getStatus(),
        who,
        session == null ? "none" : "yes");
  }
}
