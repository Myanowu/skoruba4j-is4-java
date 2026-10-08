package com.myano.skoruba4j.protocol;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Serves JWKS before Spring Security so IdentityModel can load signing keys without a login
 * redirect. Covers the IS4 discovery path and the SAS default {@code /oauth2/jwks}.
 */
public final class PublicJwkSetFilter extends OncePerRequestFilter {

  static final Set<String> PATHS = Set.of(Is4Paths.JWKS, Is4Paths.OAUTH2_JWKS);

  private final JWKSource<SecurityContext> jwkSource;
  private final JWKSelector jwkSelector = new JWKSelector(new JWKMatcher.Builder().build());

  public PublicJwkSetFilter(JWKSource<SecurityContext> jwkSource) {
    this.jwkSource = jwkSource;
  }

  static boolean matches(HttpServletRequest request) {
    if (request == null || !HttpMethod.GET.matches(request.getMethod())) {
      return false;
    }
    return PATHS.contains(pathWithinApplication(request));
  }

  static String pathWithinApplication(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri == null) {
      return "";
    }
    String context = request.getContextPath();
    if (context != null && !context.isEmpty() && uri.startsWith(context)) {
      uri = uri.substring(context.length());
    }
    int query = uri.indexOf(';');
    if (query >= 0) {
      uri = uri.substring(0, query);
    }
    return uri;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!matches(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    JWKSet jwkSet;
    try {
      jwkSet = new JWKSet(jwkSource.get(jwkSelector, null));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to select the JWK(s)", ex);
    }
    byte[] body = jwkSet.toString().getBytes(StandardCharsets.UTF_8);
    response.setStatus(HttpServletResponse.SC_OK);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/jwk-set+json;charset=UTF-8");
    response.setHeader("Cache-Control", "no-store");
    response.setContentLength(body.length);
    response.getOutputStream().write(body);
  }
}
