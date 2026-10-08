package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.protocol.AccountChooser;
import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Google-style “choose an account” for the current STS session. */
public final class AccountChooserPage {
  private AccountChooserPage() {}

  public static String render(
      String displayName, String emailOrUser, String returnUrl, String clientHint) {
    String safeReturn = Is4ReturnUrls.isSafe(returnUrl) ? returnUrl : "";
    String encReturn =
        safeReturn.isEmpty()
            ? ""
            : URLEncoder.encode(safeReturn, StandardCharsets.UTF_8);
    String titleLine =
        clientHint != null && !clientHint.isBlank()
            ? "Continue to " + clientHint
            : "Continue with your account";
    String primary = blank(displayName, emailOrUser);
    String secondary =
        emailOrUser != null
                && !emailOrUser.isBlank()
                && !emailOrUser.equalsIgnoreCase(primary)
            ? emailOrUser
            : "";
    String initial = initial(primary);

    StringBuilder inner = new StringBuilder();
    if (!safeReturn.isEmpty()) {
      inner.append("<a class=\"account\" href=\"")
          .append(StsPages.esc(AccountChooser.CHOOSE_PATH + "/continue?ReturnUrl=" + encReturn))
          .append("\">");
    } else {
      inner.append("<a class=\"account\" href=\"/\">");
    }
    inner.append("<span class=\"avatar\" aria-hidden=\"true\">")
        .append(StsPages.esc(initial))
        .append("</span>");
    inner.append("<span class=\"account-text\">");
    inner.append("<strong>").append(StsPages.esc(primary)).append("</strong>");
    if (!secondary.isBlank()) {
      inner.append("<span class=\"muted\">").append(StsPages.esc(secondary)).append("</span>");
    }
    inner.append("</span></a>");

    String otherHref =
        AccountChooser.CHOOSE_PATH
            + "/other"
            + (encReturn.isEmpty() ? "" : "?ReturnUrl=" + encReturn);
    inner.append("<a class=\"account account-other\" href=\"")
        .append(StsPages.esc(otherHref))
        .append("\">");
    inner.append("<span class=\"avatar avatar-muted\" aria-hidden=\"true\">+</span>");
    inner.append("<span class=\"account-text\"><strong>Use another account</strong></span>");
    inner.append("</a>");

    return StsPages.document(
        "Choose an account",
        "auth",
        StsPages.card("Skoruba4j STS", "Choose an account", titleLine, inner.toString()));
  }

  private static String blank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary.trim();
    }
    if (fallback != null && !fallback.isBlank()) {
      return fallback.trim();
    }
    return "Signed-in user";
  }

  private static String initial(String text) {
    if (text == null || text.isBlank()) {
      return "?";
    }
    int cp = text.trim().codePointAt(0);
    return new String(Character.toChars(Character.toUpperCase(cp)));
  }
}
