package com.myano.skoruba4j.sts.security.externallogin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Session keys for Google OAuth → Identity link flow. */
public final class ExternalLoginSession {
  public static final String CLIENT_ID = "skoruba4j.external.clientId";
  public static final String PENDING = "skoruba4j.external.pending";

  private ExternalLoginSession() {}

  public static void setClientId(HttpServletRequest request, String clientId) {
    if (request == null) {
      return;
    }
    HttpSession session = request.getSession(true);
    if (clientId == null || clientId.isBlank()) {
      session.removeAttribute(CLIENT_ID);
    } else {
      session.setAttribute(CLIENT_ID, clientId.trim());
    }
  }

  public static String clientId(HttpServletRequest request) {
    if (request == null || request.getSession(false) == null) {
      return null;
    }
    Object value = request.getSession(false).getAttribute(CLIENT_ID);
    return value == null ? null : String.valueOf(value);
  }

  public static void setPending(HttpServletRequest request, PendingExternalLogin pending) {
    if (request == null) {
      return;
    }
    HttpSession session = request.getSession(true);
    if (pending == null) {
      session.removeAttribute(PENDING);
    } else {
      session.setAttribute(PENDING, pending);
    }
  }

  public static PendingExternalLogin pending(HttpServletRequest request) {
    if (request == null || request.getSession(false) == null) {
      return null;
    }
    Object value = request.getSession(false).getAttribute(PENDING);
    return value instanceof PendingExternalLogin pending ? pending : null;
  }

  public static void clearPending(HttpServletRequest request) {
    setPending(request, null);
  }
}
