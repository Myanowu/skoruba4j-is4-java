package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.identity.UserLogin;
import java.util.List;

final class SignedInPage {
  private SignedInPage() {}

  static String render(
      String userId,
      String userName,
      String email,
      boolean twoFactorEnabled,
      List<UserLogin> logins,
      String ok) {
    StringBuilder inner = new StringBuilder();
    if ("unlinked".equals(ok)) {
      inner.append(StsPages.notice("ok", "External sign-in unlinked."));
    } else if ("missing".equals(ok)) {
      inner.append(StsPages.notice("err", "That external sign-in was not found."));
    }
    inner.append("<section class=\"tile\" style=\"margin-bottom:1rem\">");
    inner.append("<h2>Profile</h2>");
    inner.append("<dl class=\"dl\">");
    inner.append("<dt>UserName</dt><dd>").append(esc(blank(userName))).append("</dd>");
    inner.append("<dt>Email</dt><dd>").append(esc(blank(email))).append("</dd>");
    inner.append("<dt>Id</dt><dd><code>").append(esc(blank(userId))).append("</code></dd>");
    inner.append("<dt>Two-factor</dt><dd>").append(twoFactorEnabled ? "enabled" : "off").append("</dd>");
    inner.append("</dl>");
    inner.append("<p class=\"muted\" style=\"margin-top:.75rem\">Token <code>sub</code> is this user id.</p>");
    inner.append("</section>");

    inner.append("<section class=\"tile\" style=\"margin-bottom:1rem\">");
    inner.append("<h2>External sign-ins</h2>");
    if (logins == null || logins.isEmpty()) {
      inner.append("<p class=\"muted\">No linked Google / Microsoft / WeChat / WhatsApp providers.</p>");
    } else {
      inner.append("<table><thead><tr><th>Provider</th><th>Key</th><th></th></tr></thead><tbody>");
      for (UserLogin login : logins) {
        inner.append("<tr><td>")
            .append(esc(blank(displayName(login))))
            .append("</td><td><code>")
            .append(esc(blank(login.providerKey())))
            .append("</code></td><td>");
        inner.append("<form method=\"post\" action=\"/account/providers/delete\" style=\"display:inline\">");
        inner.append(StsPages.hidden("loginProvider", login.loginProvider()));
        inner.append(StsPages.hidden("providerKey", login.providerKey()));
        inner.append("<button type=\"submit\">Unlink</button></form></td></tr>");
      }
      inner.append("</tbody></table>");
    }
    inner.append("</section>");

    inner.append("<p id=\"grantsHelp\" hidden>");
    inner.append("Grants are IdentityServer PersistedGrants: they remember that this user authorized a client. ");
    inner.append("Typical types are authorization_code (the short login code), refresh_token (so an app can get new access tokens without another sign-in), and reference tokens. ");
    inner.append("Configure them when an app should stay signed in, or revoke here to cut that app off until the user consents again. ");
    inner.append("Access tokens already issued by this STS process may still work from memory until they expire.");
    inner.append("</p>");
    inner.append("<div class=\"grid\">");
    inner.append(actionTile("Grants", "Stored consents for apps this account has authorized. Used for refresh tokens and to revoke an app without changing the password.", "openGrants()", "Open grants"));
    inner.append(
        tile(
            "Change password",
            "Update the password used at this login page.",
            "/account/password",
            "Change password",
            false));
    inner.append(jsonTile("Discovery", "OpenID Provider configuration for relying parties.", "/.well-known/openid-configuration", "Open discovery"));
    inner.append(jsonTile("Health", "Process liveness for operators.", "/health", "Open /health"));
    inner.append("</div>");
    return StsPages.document("Signed in", "app-body", StsPages.app("Signed in", inner.toString()));
  }

  private static String displayName(UserLogin login) {
    if (login.providerDisplayName() != null && !login.providerDisplayName().isBlank()) {
      return login.providerDisplayName();
    }
    return login.loginProvider();
  }

  private static String tile(String title, String copy, String href, String action, boolean outline) {
    return "<article class=\"tile\"><h2>"
        + esc(title)
        + "</h2><p>"
        + esc(copy)
        + "</p><a class=\"btn"
        + (outline ? " outline" : "")
        + "\" href=\""
        + esc(href)
        + "\">"
        + esc(action)
        + "</a></article>";
  }

  private static String actionTile(String title, String copy, String onclick, String action) {
    return "<article class=\"tile\"><h2>"
        + esc(title)
        + "</h2><p>"
        + esc(copy)
        + "</p><button type=\"button\" class=\"btn\" onclick=\""
        + esc(onclick)
        + "\">"
        + esc(action)
        + "</button></article>";
  }

  private static String jsonTile(String title, String copy, String url, String action) {
    return "<article class=\"tile\"><h2>"
        + esc(title)
        + "</h2><p>"
        + esc(copy)
        + "</p><button type=\"button\" class=\"btn outline\" onclick=\"openJson('"
        + esc(url)
        + "','"
        + esc(title)
        + "')\">"
        + esc(action)
        + "</button></article>";
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private static String esc(String value) {
    return LoginPage.esc(value);
  }
}
