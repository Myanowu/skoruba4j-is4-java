package com.myano.skoruba4j.admin.i18n;

import com.myano.skoruba4j.i18n.UiLocale;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Resolves {@link UiLocale} from cookie / Accept-Language for the request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UiLocaleFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    String query = request.getQueryString();
    if (query != null && !query.isBlank()) {
      path = path + "?" + query;
    }
    try {
      UiLocale.setCurrent(
          UiLocale.resolve(cookie(request, UiLocale.COOKIE), request.getHeader("Accept-Language")));
      UiLocale.setRequestPath(path);
      filterChain.doFilter(request, response);
    } finally {
      UiLocale.clearCurrent();
    }
  }

  private static String cookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
