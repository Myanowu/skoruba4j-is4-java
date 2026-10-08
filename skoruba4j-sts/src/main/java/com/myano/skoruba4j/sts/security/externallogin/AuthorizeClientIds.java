package com.myano.skoruba4j.sts.security.externallogin;

import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/** Resolve OAuth {@code client_id} for the in-flight authorize / login. */
public final class AuthorizeClientIds {
  private AuthorizeClientIds() {}

  public static Optional<String> resolve(HttpServletRequest request) {
    if (request == null) {
      return Optional.empty();
    }
    String fromSession = ExternalLoginSession.clientId(request);
    if (fromSession != null && !fromSession.isBlank()) {
      return Optional.of(fromSession.trim());
    }
    String returnUrl = request.getParameter("ReturnUrl");
    Optional<String> fromReturn = fromUrl(returnUrl);
    if (fromReturn.isPresent()) {
      return fromReturn;
    }
    RequestCache cache = new HttpSessionRequestCache();
    SavedRequest saved = cache.getRequest(request, null);
    if (saved != null) {
      Optional<String> fromSaved = fromUrl(saved.getRedirectUrl());
      if (fromSaved.isPresent()) {
        return fromSaved;
      }
    }
    return Optional.empty();
  }

  public static Optional<String> fromUrl(String url) {
    if (url == null || url.isBlank()) {
      return Optional.empty();
    }
    String raw = url.trim();
    try {
      if (!raw.contains("://") && raw.startsWith("/")) {
        raw = "http://local.invalid" + raw;
      }
      URI uri = URI.create(raw);
      String query = uri.getRawQuery();
      if (query == null || query.isBlank()) {
        int q = url.indexOf('?');
        if (q >= 0 && q < url.length() - 1) {
          query = url.substring(q + 1);
        }
      }
      if (query == null) {
        return Optional.empty();
      }
      for (String part : query.split("&")) {
        int eq = part.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String name = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
        if (!"client_id".equalsIgnoreCase(name)) {
          continue;
        }
        String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
        if (value != null && !value.isBlank()) {
          return Optional.of(value.trim());
        }
      }
    } catch (Exception ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  /** Persist client_id from a safe ReturnUrl into the session for the Google round-trip. */
  public static void rememberFromReturnUrl(HttpServletRequest request, String returnUrl) {
    if (!Is4ReturnUrls.isSafe(returnUrl)) {
      return;
    }
    fromUrl(returnUrl).ifPresent(id -> ExternalLoginSession.setClientId(request, id));
  }
}
