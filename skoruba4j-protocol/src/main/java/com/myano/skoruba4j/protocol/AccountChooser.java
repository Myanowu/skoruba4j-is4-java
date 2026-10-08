package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Browser account picker before issuing an authorization code (Google / Microsoft style “Continue
 * as”). One STS session = one user; multi-account lists are out of scope.
 */
public final class AccountChooser {
  public static final String APPROVED_RETURN_URL = "skoruba4j.accountChooser.approvedReturnUrl";
  public static final String CHOOSE_PATH = "/login/choose";

  private AccountChooser() {}

  public static String chooseRedirect(String returnUrl) {
    if (!Is4ReturnUrls.isSafe(returnUrl)) {
      return CHOOSE_PATH;
    }
    return CHOOSE_PATH
        + "?ReturnUrl="
        + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
  }

  public static String currentAuthorizeReturnUrl(HttpServletRequest request) {
    String target = request.getRequestURI();
    String query = request.getQueryString();
    if (query != null && !query.isBlank()) {
      target = target + "?" + query;
    }
    return target;
  }

  public static boolean isApproved(HttpSession session, String returnUrl) {
    if (session == null || returnUrl == null || returnUrl.isBlank()) {
      return false;
    }
    Object approved = session.getAttribute(APPROVED_RETURN_URL);
    return approved != null && returnUrl.equals(approved.toString());
  }

  public static void markApproved(HttpSession session, String returnUrl) {
    if (session == null || !Is4ReturnUrls.isSafe(returnUrl)) {
      return;
    }
    session.setAttribute(APPROVED_RETURN_URL, returnUrl);
  }

  public static void clearApproved(HttpSession session) {
    if (session != null) {
      session.removeAttribute(APPROVED_RETURN_URL);
    }
  }

  /** OIDC {@code prompt=none} must stay silent. */
  public static boolean skipForPrompt(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return false;
    }
    for (String part : prompt.split("[\\s+]+")) {
      if ("none".equalsIgnoreCase(part.trim())) {
        return true;
      }
    }
    return false;
  }

  public static boolean wantsSelectAccount(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return false;
    }
    for (String part : prompt.split("[\\s+]+")) {
      String p = part.trim().toLowerCase(Locale.ROOT);
      if ("select_account".equals(p) || "login".equals(p)) {
        return true;
      }
    }
    return false;
  }
}
