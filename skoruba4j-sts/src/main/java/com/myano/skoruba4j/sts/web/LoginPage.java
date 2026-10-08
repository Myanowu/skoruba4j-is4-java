package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Self-drawn login HTML (not Skoruba cshtml). */
public final class LoginPage {
  private LoginPage() {}

  public static String render(
      boolean error,
      boolean logout,
      String csrfParameterName,
      String csrfToken,
      String returnUrl) {
    return render(
        error,
        logout,
        csrfParameterName,
        csrfToken,
        returnUrl,
        null,
        false,
        false,
        false,
        false,
        null,
        null);
  }

  public static String render(
      boolean error,
      boolean logout,
      String csrfParameterName,
      String csrfToken,
      String returnUrl,
      String errorCode,
      boolean showGoogle,
      boolean showMicrosoft) {
    return render(
        error,
        logout,
        csrfParameterName,
        csrfToken,
        returnUrl,
        errorCode,
        showGoogle,
        showMicrosoft,
        false,
        false,
        null,
        null);
  }

  public static String render(
      boolean error,
      boolean logout,
      String csrfParameterName,
      String csrfToken,
      String returnUrl,
      String errorCode,
      boolean showGoogle,
      boolean showMicrosoft,
      boolean showWhatsApp) {
    return render(
        error,
        logout,
        csrfParameterName,
        csrfToken,
        returnUrl,
        errorCode,
        showGoogle,
        showMicrosoft,
        showWhatsApp,
        false,
        null,
        null);
  }

  public static String render(
      boolean error,
      boolean logout,
      String csrfParameterName,
      String csrfToken,
      String returnUrl,
      String errorCode,
      boolean showGoogle,
      boolean showMicrosoft,
      boolean showWhatsApp,
      boolean showWeChat) {
    return render(
        error,
        logout,
        csrfParameterName,
        csrfToken,
        returnUrl,
        errorCode,
        showGoogle,
        showMicrosoft,
        showWhatsApp,
        showWeChat,
        null,
        null);
  }

  public static String render(
      boolean error,
      boolean logout,
      String csrfParameterName,
      String csrfToken,
      String returnUrl,
      String errorCode,
      boolean showGoogle,
      boolean showMicrosoft,
      boolean showWhatsApp,
      boolean showWeChat,
      String whatsappQrCode,
      String whatsappPrefill) {
    String param = csrfParameterName == null || csrfParameterName.isBlank() ? "_csrf" : csrfParameterName;
    String token = csrfToken == null ? "" : csrfToken;
    String action = "/login";
    if (Is4ReturnUrls.isSafe(returnUrl)) {
      action = "/login?ReturnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
    }
    StringBuilder form = new StringBuilder();
    if (errorCode != null && !errorCode.isBlank()) {
      form.append(StsPages.notice("err", externalErrorMessage(errorCode)));
    } else if (error) {
      form.append(StsPages.notice("err", "Invalid username or password."));
    }
    if (logout) {
      form.append(StsPages.notice("ok", "You are signed out."));
    }
    boolean hasQr =
        showWhatsApp && whatsappQrCode != null && !whatsappQrCode.isBlank();
    if (hasQr) {
      form.append("<div class=\"wa-inline\">");
      form.append("<p class=\"lead\" style=\"margin-bottom:.5rem\">Scan with WhatsApp</p>");
      form.append("<p class=\"wa-qr\"><img alt=\"WhatsApp login QR\" width=\"220\" height=\"220\" src=\"")
          .append(esc("/external/whatsapp/qr.png?code=" + whatsappQrCode))
          .append("\"></p>");
      if (whatsappPrefill != null && !whatsappPrefill.isBlank()) {
        form.append("<p class=\"muted\">Send: <code>")
            .append(esc(whatsappPrefill))
            .append("</code></p>");
      }
      form.append("<p id=\"waStatus\" class=\"muted\">Waiting for WhatsApp reply…</p>");
      form.append("<script>");
      form.append("(function(){var code=")
          .append(jsString(whatsappQrCode))
          .append(";");
      form.append("function tick(){fetch('/external/whatsapp/status?code='+encodeURIComponent(code),{credentials:'same-origin'})");
      form.append(".then(function(r){return r.json();}).then(function(j){");
      form.append("var el=document.getElementById('waStatus');");
      form.append("if(!j||!j.status){return;}");
      form.append("if(j.status==='ready'||j.status==='need_confirm'){");
      form.append("if(el)el.textContent='Verified — signing you in…';");
      form.append("window.location='/external/whatsapp/complete?code='+encodeURIComponent(code);return;}");
      form.append("if(j.status==='failed'){window.location='/login?error='+encodeURIComponent(j.error||'external');return;}");
      form.append("if(j.status==='expired'){if(el)el.textContent='QR expired — refresh the page.';return;}");
      form.append("setTimeout(tick,1500);}).catch(function(){setTimeout(tick,2000);});}");
      form.append("setTimeout(tick,1200);})();");
      form.append("</script></div>");
      form.append("<p class=\"login-or\"><span>or password</span></p>");
    }
    form.append("<form method=\"post\" action=\"").append(esc(action)).append("\">");
    form.append("<label for=\"username\">Username or email</label>");
    form.append(StsPages.field("username", "username", "text", "username", true));
    form.append("<div class=\"row\"><label for=\"password\">Password</label>");
    form.append("<a href=\"/forgot-password\">Forgot your password?</a></div>");
    form.append(StsPages.passwordField("password", "password", "current-password", false));
    form.append(StsPages.hidden(param, token));
    if (returnUrl != null && !returnUrl.isBlank()) {
      form.append(StsPages.hidden("ReturnUrl", returnUrl));
    }
    form.append("<button class=\"primary\" type=\"submit\">Sign in</button>");
    form.append("</form>");
    if (showGoogle || showMicrosoft || (showWhatsApp && !hasQr) || showWeChat) {
      form.append("<p class=\"login-or\"><span>or</span></p>");
    }
    if (showGoogle) {
      form.append("<p class=\"oidc-primary\"><a class=\"sign-in\" href=\"/oauth2/authorization/google\">");
      form.append("Sign in with Google</a></p>");
    }
    if (showMicrosoft) {
      form.append(
          "<p class=\"oidc-primary\"><a class=\"sign-in\" href=\"/oauth2/authorization/microsoft\">");
      form.append("Sign in with Microsoft</a></p>");
    }
    if (showWhatsApp && !hasQr) {
      form.append("<p class=\"oidc-primary\"><a class=\"sign-in\" href=\"/external/whatsapp\">");
      form.append("Sign in with WhatsApp (QR)</a></p>");
    }
    if (showWeChat) {
      form.append("<p class=\"oidc-primary\"><a class=\"sign-in\" href=\"/external/wechat\">");
      form.append("Sign in with WeChat (QR)</a></p>");
    }
    return StsPages.document(
        "Sign in",
        "auth",
        StsPages.card("Skoruba4j STS", "Sign in", "OpenID Provider for IdentityServer4 clients.", form.toString()));
  }

  static String externalErrorMessage(String code) {
    return switch (code == null ? "" : code) {
      case "external-no-account" ->
          "No local account matches this external sign-in. Ask an admin to create a user (matching email or phone) first, or link WeChat on an existing account.";
      case "external-disabled" ->
          "External sign-in is not enabled for this application.";
      case "external-no-email" -> "The identity provider did not return an email address.";
      case "external-no-phone" -> "WhatsApp did not provide a phone number.";
      case "external-no-subject" -> "The identity provider did not return a user id.";
      case "external-username-taken" -> "That username is already taken.";
      case "external-no-pending" -> "External sign-in session expired. Try again.";
      case "external" -> "External sign-in failed.";
      default -> "External sign-in could not be completed.";
    };
  }

  static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static String jsString(String value) {
    if (value == null) {
      return "''";
    }
    return "'"
        + value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        + "'";
  }
}
