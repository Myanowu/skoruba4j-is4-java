package com.myano.skoruba4j.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

final class AdminRequests {
  private AdminRequests() {}

  static String csrfName(HttpServletRequest request) {
    CsrfToken csrf = csrf(request);
    return csrf == null ? "_csrf" : csrf.getParameterName();
  }

  static String csrfToken(HttpServletRequest request) {
    CsrfToken csrf = csrf(request);
    return csrf == null ? "" : csrf.getToken();
  }

  static CsrfToken csrf(HttpServletRequest request) {
    CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrf == null) {
      csrf = (CsrfToken) request.getAttribute("_csrf");
    }
    return csrf;
  }

  static String flash(HttpServletRequest request) {
    var map = RequestContextUtils.getInputFlashMap(request);
    if (map == null) {
      return null;
    }
    Object value = map.get("flash");
    return value == null ? null : value.toString();
  }

  static String flashAttr(HttpServletRequest request, String name) {
    var map = RequestContextUtils.getInputFlashMap(request);
    if (map == null || name == null) {
      return null;
    }
    Object value = map.get(name);
    return value == null ? null : value.toString();
  }

  static void notice(RedirectAttributes redirect, String message) {
    redirect.addFlashAttribute("flash", message);
  }

  static void secretReveal(RedirectAttributes redirect, String plaintext) {
    redirect.addFlashAttribute("secretReveal", plaintext);
  }

  static boolean checked(HttpServletRequest request, String name) {
    String value = request.getParameter(name);
    return "on".equals(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
  }

  static int intParam(HttpServletRequest request, String name, int fallback) {
    try {
      String raw = request.getParameter(name);
      if (raw == null || raw.isBlank()) {
        return fallback;
      }
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
