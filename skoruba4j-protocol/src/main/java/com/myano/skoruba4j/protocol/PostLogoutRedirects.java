package com.myano.skoruba4j.protocol;

import java.net.URI;
import java.util.Locale;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * IdentityServer4 registers {@code post_logout_redirect_uri}. ASP.NET OpenIdConnect still sends
 * {@code /signout-callback-oidc} on the same origin as {@code /signin-oidc} even when that path is
 * missing from {@code ClientPostLogoutRedirectUris}.
 *
 * <p>Only a pure ASP.NET host (registered {@code /signin-oidc}, no SPA oidc-client callbacks on that
 * origin) gets the invented sign-out fallback. Hybrid hosts that also register {@code
 * /login-callback} / {@code /silent-callback} (Gatsby + YARP on {@code :8000}) must list the
 * sign-out URI explicitly — otherwise the SPA 404s and STS shows no error.
 */
public final class PostLogoutRedirects {
  private PostLogoutRedirects() {}

  /** True when {@code uri} is registered or is the ASP.NET sign-out callback next to /signin-oidc. */
  public static boolean allowed(RegisteredClient client, String uri) {
    if (client == null || uri == null || uri.isBlank()) {
      return false;
    }
    if (client.getPostLogoutRedirectUris().contains(uri) || client.getRedirectUris().contains(uri)) {
      return true;
    }
    return isAspNetSignOutCallback(uri) && sameOriginAsSignInOidc(uri, client.getRedirectUris());
  }

  static boolean isAspNetSignOutCallback(String uri) {
    String path = path(uri);
    return "/signout-callback-oidc".equals(path) || "/signout-oidc".equals(path);
  }

  /**
   * ASP.NET OpenIdConnect pairs {@code /signout-callback-oidc} with the host that serves {@code
   * /signin-oidc}, not with an arbitrary SPA redirect on another port. Origins that also expose
   * SPA oidc-client callbacks are excluded unless the sign-out URI is registered explicitly.
   */
  static boolean sameOriginAsSignInOidc(String uri, Iterable<String> redirectUris) {
    String origin = origin(uri);
    if (origin == null || redirectUris == null) {
      return false;
    }
    if (originHasSpaOidcCallbacks(origin, redirectUris)) {
      return false;
    }
    for (String other : redirectUris) {
      if (other == null || other.isBlank()) {
        continue;
      }
      String path = path(other);
      if (!"/signin-oidc".equals(path)) {
        continue;
      }
      if (origin.equals(origin(other))) {
        return true;
      }
    }
    return false;
  }

  /** oidc-client / react-oidc-context style paths often co-exist with a proxied /signin-oidc. */
  static boolean originHasSpaOidcCallbacks(String origin, Iterable<String> redirectUris) {
    for (String other : redirectUris) {
      if (other == null || other.isBlank() || !origin.equals(origin(other))) {
        continue;
      }
      String path = path(other);
      if (path == null) {
        continue;
      }
      if (path.endsWith("/login-callback")
          || path.endsWith("/silent-callback")
          || path.endsWith("/silent-renew")
          || "/callback".equals(path)
          || path.endsWith("/authentication/login-callback")) {
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
