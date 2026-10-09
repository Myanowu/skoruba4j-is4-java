package com.myano.skoruba4j.sts.security;

import jakarta.servlet.http.HttpSession;

/** Session keys for the optional TOTP step after password verification. */
public final class TwoFactorLogin {
  public static final String SESSION_USER_ID = "skoruba4j.2fa.userId";
  public static final String SESSION_RETURN_URL = "skoruba4j.2fa.returnUrl";

  private TwoFactorLogin() {}

  public static void begin(HttpSession session, String userId, String returnUrl) {
    session.setAttribute(SESSION_USER_ID, userId);
    if (returnUrl != null && !returnUrl.isBlank()) {
      session.setAttribute(SESSION_RETURN_URL, returnUrl);
    } else {
      session.removeAttribute(SESSION_RETURN_URL);
    }
  }

  public static String userId(HttpSession session) {
    if (session == null) {
      return null;
    }
    Object value = session.getAttribute(SESSION_USER_ID);
    return value == null ? null : value.toString();
  }

  public static String returnUrl(HttpSession session) {
    if (session == null) {
      return null;
    }
    Object value = session.getAttribute(SESSION_RETURN_URL);
    return value == null ? null : value.toString();
  }

  public static void clear(HttpSession session) {
    if (session == null) {
      return;
    }
    session.removeAttribute(SESSION_USER_ID);
    session.removeAttribute(SESSION_RETURN_URL);
  }
}
