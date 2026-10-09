package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * After RP logout the STS session is gone, so {@code SavedRequest} cannot resume authorize. Remember
 * the client so the next form login (or an immediate post-logout redirect) returns to that RP's
 * {@code /login} and restarts OIDC — not the STS home page.
 */
public final class RpResume {
  public static final String COOKIE = "idserver.rp_client";

  private RpResume() {}

  public static void store(
      HttpServletRequest request, HttpServletResponse response, String clientId) {
    if (response == null || clientId == null || clientId.isBlank()) {
      return;
    }
    Cookie cookie = new Cookie(COOKIE, clientId.trim());
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(900);
    cookie.setSecure(request != null && request.isSecure());
    response.addCookie(cookie);
  }

  public static String read(HttpServletRequest request) {
    if (request == null || request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
        return cookie.getValue().trim();
      }
    }
    return null;
  }

  public static void clear(HttpServletResponse response) {
    if (response == null) {
      return;
    }
    Cookie cookie = new Cookie(COOKIE, "");
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }

  /**
   * Prefer the origin that hosts {@code /signin-oidc}, then {@code /login} so the RP restarts the
   * OIDC challenge (admin.web Gatsby proxies {@code /login}; skoruba Admin serves it too).
   */
  public static String target(RegisteredClient client) {
    if (client == null) {
      return null;
    }
    String fallback = null;
    for (String redirect : client.getRedirectUris()) {
      String origin = PostLogoutRedirects.origin(redirect);
      if (origin == null) {
        continue;
      }
      String login = origin + "/login";
      if (redirect.contains("/signin-oidc")) {
        return login;
      }
      if (fallback == null) {
        fallback = login;
      }
    }
    return fallback;
  }

  public static String targetForClientIds(RegisteredClientRepository clients, String... clientIds) {
    if (clients == null || clientIds == null) {
      return null;
    }
    for (String clientId : clientIds) {
      if (clientId == null || clientId.isBlank()) {
        continue;
      }
      String resume = target(clients.findByClientId(clientId.trim()));
      if (resume != null) {
        return resume;
      }
    }
    return null;
  }
}
