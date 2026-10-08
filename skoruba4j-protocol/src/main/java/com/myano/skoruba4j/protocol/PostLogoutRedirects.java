package com.myano.skoruba4j.protocol;

import java.net.URI;
import java.util.Locale;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * IdentityServer4 registers {@code post_logout_redirect_uri}. ASP.NET OpenIdConnect still sends
 * {@code /signout-callback-oidc} on the same origin as {@code /signin-oidc} even when that path is
 * missing from {@code ClientPostLogoutRedirectUris}.
 */
public final class PostLogoutRedirects {
  private PostLogoutRedirects() {}

  public static boolean allowed(RegisteredClient client, String uri) {
    if (client == null || uri == null || uri.isBlank()) {
      return false;
    }
    if (client.getPostLogoutRedirectUris().contains(uri) || client.getRedirectUris().contains(uri)) {
      return true;
    }
    return isAspNetSignOutCallback(uri) && sameOriginAsAny(uri, client.getRedirectUris());
  }

  static boolean isAspNetSignOutCallback(String uri) {
    String path = path(uri);
    return "/signout-callback-oidc".equals(path) || "/signout-oidc".equals(path);
  }

  static boolean sameOriginAsAny(String uri, Iterable<String> others) {
    String origin = origin(uri);
    if (origin == null) {
      return false;
    }
    for (String other : others) {
      if (origin.equals(origin(other))) {
        return true;
      }
    }
    return false;
  }

  static String origin(String uri) {
    if (uri == null || uri.isBlank()) {
      return null;
    }
    try {
      URI parsed = URI.create(uri.trim());
      String scheme = parsed.getScheme();
      String host = parsed.getHost();
      if (scheme == null || host == null || host.isBlank()) {
        return null;
      }
      String lower = scheme.toLowerCase(Locale.ROOT);
      if (!"https".equals(lower) && !"http".equals(lower)) {
        return null;
      }
      StringBuilder origin = new StringBuilder(lower).append("://").append(host.toLowerCase(Locale.ROOT));
      int port = parsed.getPort();
      if (port != -1) {
        origin.append(':').append(port);
      }
      return origin.toString();
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  static String path(String uri) {
    try {
      String path = URI.create(uri.trim()).getPath();
      if (path != null && path.endsWith("/") && path.length() > 1) {
        return path.substring(0, path.length() - 1);
      }
      return path;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
