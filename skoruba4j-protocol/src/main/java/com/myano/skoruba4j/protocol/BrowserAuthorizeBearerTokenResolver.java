package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * JWT resource-server stays on the authorization-server chain for userinfo, but browser {@code
 * /connect/authorize} must use the form-login session — not a Bearer token (or the lack of one).
 */
public final class BrowserAuthorizeBearerTokenResolver implements BearerTokenResolver {
  private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    if (isBrowserEndpoint(request)) {
      return null;
    }
    return delegate.resolve(request);
  }

  static boolean isBrowserEndpoint(HttpServletRequest request) {
    String path = PublicJwkSetFilter.pathWithinApplication(request);
    return Is4Paths.AUTHORIZE.equals(path)
        || "/connect/authorize/callback".equals(path)
        || Is4Paths.END_SESSION.equals(path);
  }
}
