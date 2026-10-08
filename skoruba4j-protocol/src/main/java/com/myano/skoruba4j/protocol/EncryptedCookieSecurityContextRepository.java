package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Encrypted auth cookie analogous to ASP.NET Identity application cookie + Data Protection: survives
 * STS process restart when the key file is stable. Session attributes (OAuth state, external pending)
 * still use HttpSession separately.
 */
public final class EncryptedCookieSecurityContextRepository implements SecurityContextRepository {
  private static final Logger log =
      LoggerFactory.getLogger(EncryptedCookieSecurityContextRepository.class);

  public static final String DEFAULT_COOKIE = "SKORUBA4J_STS_AUTH";

  private final AuthCookieCipher cipher;
  private final String cookieName;
  private final Duration ttl;
  private final boolean secure;

  public EncryptedCookieSecurityContextRepository(
      AuthCookieCipher cipher, String cookieName, Duration ttl, boolean secure) {
    this.cipher = cipher;
    this.cookieName = cookieName == null || cookieName.isBlank() ? DEFAULT_COOKIE : cookieName;
    this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofHours(10) : ttl;
    this.secure = secure;
  }

  @Override
  @Deprecated
  public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
    return load(requestResponseHolder.getRequest());
  }

  @Override
  public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
    return new DeferredSecurityContext() {
      private SecurityContext context;
      private boolean loaded;

      @Override
      public SecurityContext get() {
        if (!loaded) {
          context = load(request);
          loaded = true;
        }
        return context;
      }

      @Override
      public boolean isGenerated() {
        Authentication auth = get().getAuthentication();
        return auth == null
            || !auth.isAuthenticated()
            || auth instanceof AnonymousAuthenticationToken;
      }
    };
  }

  @Override
  public void saveContext(
      SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
    Authentication auth = context == null ? null : context.getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || auth instanceof AnonymousAuthenticationToken) {
      clearCookie(response);
      return;
    }
    try {
      String payload = serialize(auth, Instant.now().plus(ttl));
      String token = cipher.encrypt(payload);
      Cookie cookie = new Cookie(cookieName, token);
      cookie.setHttpOnly(true);
      cookie.setPath("/");
      cookie.setMaxAge((int) Math.min(Integer.MAX_VALUE, ttl.toSeconds()));
      cookie.setSecure(secure || request.isSecure());
      // Servlet Cookie API: SameSite via response header for broader container support
      response.addHeader(
          "Set-Cookie",
          cookieName
              + "="
              + token
              + "; Path=/; HttpOnly; Max-Age="
              + cookie.getMaxAge()
              + "; SameSite=Lax"
              + (cookie.getSecure() ? "; Secure" : ""));
    } catch (Exception e) {
      log.warn("Failed to write STS auth cookie: {}", e.getMessage());
      clearCookie(response);
    }
  }

  @Override
  public boolean containsContext(HttpServletRequest request) {
    Authentication auth = load(request).getAuthentication();
    return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
  }

  private SecurityContext load(HttpServletRequest request) {
    String raw = readCookie(request);
    if (raw == null || raw.isBlank()) {
      return empty();
    }
    try {
      String payload = cipher.decrypt(raw);
      Authentication auth = deserialize(payload);
      if (auth == null) {
        return empty();
      }
      SecurityContext context = new SecurityContextImpl();
      context.setAuthentication(auth);
      return context;
    } catch (Exception e) {
      log.debug("STS auth cookie ignored: {}", e.getMessage());
      return empty();
    }
  }

  private void clearCookie(HttpServletResponse response) {
    response.addHeader(
        "Set-Cookie",
        cookieName + "=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
  }

  private String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  static String serialize(Authentication auth, Instant expiresAt) {
    String name = auth.getName() == null ? "" : auth.getName();
    StringBuilder authorities = new StringBuilder();
    for (GrantedAuthority a : auth.getAuthorities()) {
      if (a == null || a.getAuthority() == null || a.getAuthority().isBlank()) {
        continue;
      }
      if (authorities.length() > 0) {
        authorities.append(',');
      }
      authorities.append(a.getAuthority().replace(',', '_'));
    }
    return "v1|"
        + expiresAt.getEpochSecond()
        + "|"
        + encode(name)
        + "|"
        + encode(authorities.toString());
  }

  static Authentication deserialize(String payload) {
    if (payload == null || !payload.startsWith("v1|")) {
      return null;
    }
    String[] parts = payload.split("\\|", 4);
    if (parts.length < 4) {
      return null;
    }
    long exp = Long.parseLong(parts[1]);
    if (Instant.now().getEpochSecond() > exp) {
      return null;
    }
    String username = decode(parts[2]);
    if (username.isBlank()) {
      return null;
    }
    List<GrantedAuthority> authorities = new ArrayList<>();
    String authPart = decode(parts[3]);
    if (!authPart.isBlank()) {
      for (String a : authPart.split(",")) {
        if (!a.isBlank()) {
          authorities.add(new SimpleGrantedAuthority(a));
        }
      }
    }
    User principal = new User(username, "N/A", authorities);
    return UsernamePasswordAuthenticationToken.authenticated(principal, "N/A", authorities);
  }

  private static String encode(String value) {
    return java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String decode(String value) {
    return new String(
        java.util.Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
  }

  private static SecurityContext empty() {
    return SecurityContextHolder.createEmptyContext();
  }
}
