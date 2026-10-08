package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * IdentityServer4 {@code ReturnUrl}: after form login, resume {@code /connect/authorize...} instead
 * of the STS home page.
 */
public final class Is4ReturnUrls {
  private Is4ReturnUrls() {}

  public static boolean isSafe(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    if (url.startsWith("//") || url.indexOf('\\') >= 0) {
      return false;
    }
    String pathAndQuery = url;
    if (url.startsWith("https://") || url.startsWith("http://")) {
      int pathAt = url.indexOf('/', url.startsWith("https://") ? 8 : 7);
      if (pathAt < 0) {
        return false;
      }
      pathAndQuery = url.substring(pathAt);
    }
    int queryAt = pathAndQuery.indexOf('?');
    String path = queryAt >= 0 ? pathAndQuery.substring(0, queryAt) : pathAndQuery;
    return "/connect/authorize".equals(path) || "/connect/authorize/callback".equals(path);
  }

  public static String loginRedirect(HttpServletRequest request) {
    String target = request.getRequestURI();
    String query = request.getQueryString();
    if (query != null && !query.isBlank()) {
      target = target + "?" + query;
    }
    if (!isSafe(target)) {
      return "/login";
    }
    return "/login?ReturnUrl=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
  }
}
