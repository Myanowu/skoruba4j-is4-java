package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.admin.security.AdminIdentityUser;
import com.myano.skoruba4j.admin.security.JwtPayloads;
import com.myano.skoruba4j.admin.security.StsSessionUser;
import com.myano.skoruba4j.i18n.LangSwitcher;
import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.i18n.UiLocale;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Self-drawn Admin HTML (not Skoruba cshtml). */
public final class AdminHtml {
  private AdminHtml() {}

  public static String page(String title, String flash, String csrfName, String csrfToken, String body) {
    boolean authed = signedIn() != null;
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"")
        .append(esc(UiLocale.current().htmlLang()))
        .append("\"><head><meta charset=\"UTF-8\">");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    html.append("<title>").append(esc(title)).append(" · Skoruba4j Admin</title>");
    html.append(favicon());
    html.append("<style>").append(css()).append("</style></head><body>");
    if (authed) {
      html.append("<header><div class=\"bar\">");
      html.append("<a class=\"brand\" href=\"/admin\"><span class=\"mark\"></span>Skoruba4j</a>");
      html.append("<nav>");
      html.append("<a href=\"/admin/clients\">").append(esc(Messages.t("nav.clients"))).append("</a>");
      html.append("<a href=\"/admin/users\">").append(esc(Messages.t("nav.users"))).append("</a>");
      html.append("<a href=\"/admin/roles\">").append(esc(Messages.t("nav.roles"))).append("</a>");
      html.append("<a href=\"/admin/api-resources\">")
          .append(esc(Messages.t("nav.apiResources")))
          .append("</a>");
      html.append("<a href=\"/admin/api-scopes\">")
          .append(esc(Messages.t("nav.apiScopes")))
          .append("</a>");
      html.append("<a href=\"/admin/identity-resources\">")
          .append(esc(Messages.t("nav.identityResources")))
          .append("</a>");
      html.append("<a href=\"/admin/grants\">").append(esc(Messages.t("nav.grants"))).append("</a>");
      html.append("<a href=\"/admin/audit-logs\">")
          .append(esc(Messages.t("nav.auditLogs")))
          .append("</a>");
      html.append("<a href=\"/about\">").append(esc(Messages.t("nav.about"))).append("</a>");
      html.append("</nav>");
      html.append("<div class=\"tools\">");
      html.append(LangSwitcher.html());
      html.append(accountMarkup());
      html.append("</div>");
      html.append("</div></header>");
    }
    html.append("<main>");
    if (flash != null && !flash.isBlank()) {
      html.append("<p class=\"flash\">").append(esc(flash)).append("</p>");
    }
    html.append(body);
    html.append("</main>");
    html.append(profileDialog());
    html.append(messageBoxDialog());
    html.append(messageBoxScript());
    html.append(toggleSecretScript());
    if (signedIn() != null) {
      html.append(accountScript());
    }
    html.append("</body></html>");
    return html.toString();
  }

  public static String loginPage(String title, String flash, String body) {
    return loginPage(title, flash, body, false, null, null);
  }

  /** Standalone sign-in gate. No admin nav until the user is authenticated. */
  public static String loginPage(String title, String flash, String body, boolean roleDenied) {
    return loginPage(title, flash, body, roleDenied, null, null);
  }

  /**
   * Standalone sign-in gate. When {@code roleDenied}, show required admin role vs roles on the STS
   * token (password was accepted; access was not).
   */
  public static String loginPage(
      String title,
      String flash,
      String body,
      boolean roleDenied,
      String requiredRole,
      String tokenRoles) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"")
        .append(esc(UiLocale.current().htmlLang()))
        .append("\"><head><meta charset=\"UTF-8\">");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    html.append("<title>").append(esc(title)).append(" · Skoruba4j Admin</title>");
    html.append(favicon());
    html.append("<style>").append(css()).append("</style></head><body class=\"gate\">");
    html.append(LangSwitcher.html("/login"));
    html.append("<main>");
    if (flash != null && !flash.isBlank()) {
      html.append("<p class=\"flash\">").append(esc(flash)).append("</p>");
    }
    html.append(body);
    html.append("</main>");
    if (roleDenied) {
      html.append("<dialog class=\"notice\" open><form method=\"dialog\">");
      html.append("<h2>").append(esc(Messages.t("login.denied.title"))).append("</h2>");
      html.append("<p>").append(esc(Messages.t("login.denied.body")));
      if (requiredRole != null && !requiredRole.isBlank()) {
        html.append(" <code>").append(esc(requiredRole.trim())).append("</code>");
      }
      html.append("</p>");
      html.append("<p>").append(esc(Messages.t("login.denied.roles"))).append(" <code>");
      html.append(
          tokenRoles == null || tokenRoles.isBlank()
              ? esc(Messages.t("login.denied.none"))
              : esc(tokenRoles.trim()));
      html.append("</code></p>");
      html.append("<button type=\"submit\">")
          .append(esc(Messages.t("login.ok")))
          .append("</button></form></dialog>");
    }
    html.append(messageBoxDialog());
    html.append(messageBoxScript());
    html.append(toggleSecretScript());
    html.append("</body></html>");
    return html.toString();
  }

  static String toggleSecretScript() {
    return """
        <script>
        function toggleSecret(id,btn){
          var i=document.getElementById(id);
          if(!i||!btn)return;
          var show=i.type==='password';
          i.type=show?'text':'password';
          btn.classList.toggle('is-shown',show);
          btn.setAttribute('aria-label',show?'%s':'%s');
        }
        </script>
        """
        .formatted(
            escJs(Messages.t("login.hidePassword")), escJs(Messages.t("login.showPassword")));
  }

  private static String escJs(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
  }

  /** Common OpenID claim types shown as chips (C# UserClaims suggestions). */
  public static final String[] OPENID_CLAIM_TYPES_PRIMARY = {
    "sub", "name", "given_name", "family_name", "email"
  };

  /** Extra OpenID / Identity claim types behind “More”. */
  public static final String[] OPENID_CLAIM_TYPES_MORE = {
    "email_verified",
    "preferred_username",
    "phone_number",
    "phone_number_verified",
    "picture",
    "website",
    "gender",
    "birthdate",
    "zoneinfo",
    "locale",
    "address",
    "updated_at",
    "role",
    "amr",
    "acr",
    "sid"
  };

  /**
   * User-claims add row (Skoruba style): suggestions on top, then claim type + claim value on one
   * line.
   */
  public static String claimTypePicker(String inputId, String inputName) {
    return claimTypeValueRow(inputId, inputName, "claim-value", "value");
  }

  public static String claimTypeValueRow(
      String typeId, String typeName, String valueId, String valueName) {
    String id = typeId == null || typeId.isBlank() ? "claim-type" : typeId;
    String name = typeName == null || typeName.isBlank() ? "type" : typeName;
    String vid = valueId == null || valueId.isBlank() ? "claim-value" : valueId;
    String vname = valueName == null || valueName.isBlank() ? "value" : valueName;
    String listId = id + "-list";
    StringBuilder html = new StringBuilder();
    html.append("<div class=\"claim-add\" data-claim-type>");
    html.append("<div class=\"claim-suggest\">");
    html.append("<span class=\"claim-suggest-label\">Suggestions</span>");
    html.append("<div class=\"claim-chips\">");
    for (String type : OPENID_CLAIM_TYPES_PRIMARY) {
      html.append("<button type=\"button\" class=\"chip-btn\" data-claim-chip=\"")
          .append(esc(type))
          .append("\">")
          .append(esc(type))
          .append("</button>");
    }
    html.append(
        "<button type=\"button\" class=\"chip-btn more\" data-claim-more aria-expanded=\"false\">More +</button>");
    html.append("</div>");
    html.append("<div class=\"claim-chips more-chips\" data-claim-more-panel hidden>");
    for (String type : OPENID_CLAIM_TYPES_MORE) {
      html.append("<button type=\"button\" class=\"chip-btn\" data-claim-chip=\"")
          .append(esc(type))
          .append("\">")
          .append(esc(type))
          .append("</button>");
    }
    html.append("</div></div>");
    html.append("<div class=\"grid-2 claim-fields\">");
    html.append("<div class=\"field\"><label for=\"").append(esc(id)).append("\">Claim type</label>");
    html.append("<input id=\"")
        .append(esc(id))
        .append("\" name=\"")
        .append(esc(name))
        .append("\" type=\"text\" required autocomplete=\"off\" spellcheck=\"false\" list=\"")
        .append(esc(listId))
        .append("\" placeholder=\"Type 2+ characters or pick a suggestion\">");
    html.append("<datalist id=\"").append(esc(listId)).append("\">");
    for (String type : OPENID_CLAIM_TYPES_PRIMARY) {
      html.append("<option value=\"").append(esc(type)).append("\">");
    }
    for (String type : OPENID_CLAIM_TYPES_MORE) {
      html.append("<option value=\"").append(esc(type)).append("\">");
    }
    html.append("</datalist></div>");
    html.append("<div class=\"field\"><label for=\"").append(esc(vid)).append("\">Claim value</label>");
    html.append("<input id=\"")
        .append(esc(vid))
        .append("\" name=\"")
        .append(esc(vname))
        .append("\" type=\"text\" autocomplete=\"off\" spellcheck=\"false\"></div>");
    html.append("</div></div>");
    return html.toString();
  }

  static String claimTypePickerScript() {
    return """
        <script>
        (function(){
          document.querySelectorAll('[data-claim-type]').forEach(function(root){
            var input=root.querySelector('input[type=text]');
            var more=root.querySelector('[data-claim-more]');
            var panel=root.querySelector('[data-claim-more-panel]');
            root.querySelectorAll('[data-claim-chip]').forEach(function(btn){
              btn.addEventListener('click',function(){
                if(!input) return;
                input.value=btn.getAttribute('data-claim-chip')||'';
                input.focus();
                root.querySelectorAll('[data-claim-chip]').forEach(function(b){
                  b.classList.toggle('is-active',b===btn);
                });
              });
            });
            if(more&&panel){
              more.addEventListener('click',function(){
                var open=panel.hasAttribute('hidden');
                if(open){panel.removeAttribute('hidden');}
                else{panel.setAttribute('hidden','');}
                more.setAttribute('aria-expanded',open?'true':'false');
                more.textContent=open?'Less −':'More +';
              });
            }
          });
        })();
        </script>
        """;
  }

  /** Password input with show/hide control (login + user editor). */
  public static String passwordInput(String id, String name, boolean required, String autocomplete) {
    StringBuilder html = new StringBuilder();
    html.append("<div class=\"secret\"><input id=\"")
        .append(esc(id))
        .append("\" name=\"")
        .append(esc(name))
        .append("\" type=\"password\" autocomplete=\"")
        .append(esc(autocomplete == null || autocomplete.isBlank() ? "new-password" : autocomplete))
        .append('"');
    if (required) {
      html.append(" required");
    }
    html.append(">");
    html.append("<button type=\"button\" class=\"eye\" aria-label=\"Show password\" onclick=\"toggleSecret('")
        .append(esc(id))
        .append("',this)\">");
    html.append(
        "<svg class=\"on\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z\"/><circle cx=\"12\" cy=\"12\" r=\"3\"/></svg>");
    html.append(
        "<svg class=\"off\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M3 3l18 18\"/><path d=\"M10.6 6.1A10.9 10.9 0 0 1 12 6c6.5 0 10 6 10 6a18.4 18.4 0 0 1-4.1 4.6\"/><path d=\"M6.1 6.7C3.6 8.4 2 12 2 12s3.5 7 10 7c1.5 0 2.9-.3 4.1-.9\"/><path d=\"M9.9 9.9a3 3 0 0 0 4.2 4.2\"/></svg>");
    html.append("</button></div>");
    return html.toString();
  }

  static String favicon() {
    return "<link rel=\"icon\" type=\"image/svg+xml\" href=\""
        + "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 512 512'%3E"
        + "%3Crect width='512' height='512' rx='118' fill='%236B1C24'/%3E"
        + "%3Cpath fill='%23F0E4C8' d='M256 72L420 154v112c0 123-164 184-164 184S92 389 92 266V154z'/%3E"
        + "%3Ccircle cx='256' cy='230' r='52' fill='%236B1C24'/%3E"
        + "%3Cpath fill='%236B1C24' d='M233 256h46l13 102H220z'/%3E"
        + "%3C/svg%3E\">";
  }

  public static String loginForm(
      String csrfName,
      String csrfToken,
      String error,
      String description,
      boolean oidcEnabled,
      String forgotPasswordUrl) {
    return loginForm(
        csrfName,
        csrfToken,
        error,
        description,
        oidcEnabled
            ? com.myano.skoruba4j.admin.config.AdminLoginMode.STS_OIDC
            : com.myano.skoruba4j.admin.config.AdminLoginMode.LOCAL,
        forgotPasswordUrl);
  }

  public static String loginForm(
      String csrfName,
      String csrfToken,
      String error,
      String description,
      com.myano.skoruba4j.admin.config.AdminLoginMode mode,
      String forgotPasswordUrl) {
    com.myano.skoruba4j.admin.config.AdminLoginMode loginMode =
        mode == null ? com.myano.skoruba4j.admin.config.AdminLoginMode.LOCAL : mode;
    StringBuilder body = new StringBuilder();
    body.append("<p class=\"kicker\">").append(esc(Messages.t("login.kicker.admin"))).append("</p>");
    body.append("<h1>").append(esc(Messages.t("login.title"))).append("</h1>");
    if (error != null) {
      if (error.isBlank() || "true".equalsIgnoreCase(error)) {
        body.append("<p class=\"err\">").append(esc(Messages.t("login.invalid"))).append("</p>");
      } else {
        body.append("<p class=\"err\">").append(esc(Messages.t("login.failed"))).append("</p>");
      }
    }
    if (loginMode == com.myano.skoruba4j.admin.config.AdminLoginMode.STS_OIDC) {
      body.append("<p class=\"oidc-primary\"><a class=\"sign-in\" href=\"/oauth2/authorization/sts\">")
          .append(esc(Messages.t("login.oidcSts")))
          .append("</a></p>");
      return body.toString();
    }
    body.append("<form class=\"login\" method=\"post\" action=\"/login\">");
    body.append("<label for=\"username\">")
        .append(esc(Messages.t("login.username")))
        .append("</label>");
    body.append(
        "<input id=\"username\" name=\"username\" type=\"text\" autocomplete=\"username\" required autofocus>");
    body.append("<div class=\"row\"><label for=\"password\">")
        .append(esc(Messages.t("login.password")))
        .append("</label>");
    if (forgotPasswordUrl != null && !forgotPasswordUrl.isBlank()) {
      body.append("<a class=\"forgot\" href=\"")
          .append(esc(forgotPasswordUrl))
          .append("\">")
          .append(esc(Messages.t("login.forgot")))
          .append("</a>");
    }
    body.append("</div>");
    body.append(
        "<div class=\"secret\"><input id=\"password\" name=\"password\" type=\"password\" autocomplete=\"current-password\" required>");
    body.append("<button type=\"button\" class=\"eye\" aria-label=\"")
        .append(esc(Messages.t("login.showPassword")))
        .append("\" onclick=\"toggleSecret('password',this)\">");
    body.append(
        "<svg class=\"on\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z\"/><circle cx=\"12\" cy=\"12\" r=\"3\"/></svg>");
    body.append(
        "<svg class=\"off\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M3 3l18 18\"/><path d=\"M10.6 6.1A10.9 10.9 0 0 1 12 6c6.5 0 10 6 10 6a18.4 18.4 0 0 1-4.1 4.6\"/><path d=\"M6.1 6.7C3.6 8.4 2 12 2 12s3.5 7 10 7c1.5 0 2.9-.3 4.1-.9\"/><path d=\"M9.9 9.9a3 3 0 0 0 4.2 4.2\"/></svg>");
    body.append("</button></div>");
    body.append(hiddenCsrf(csrfName, csrfToken));
    body.append("<button type=\"submit\">")
        .append(esc(Messages.t("login.submit")))
        .append("</button>");
    body.append("</form>");
    if (loginMode.usesOidcClientBeans()) {
      body.append("<p class=\"login-or\"><span>")
          .append(esc(Messages.t("login.or")))
          .append("</span></p>");
      body.append(
              "<p class=\"oidc-primary\"><a class=\"sign-in outline\" href=\"/oauth2/authorization/sts\">")
          .append(esc(Messages.t("login.oidcSts")))
          .append("</a></p>");
    }
    return body.toString();
  }

  static String css() {
    return """
        :root{--wine:#5c1219;--wine-deep:#3d0c12;--wine-mid:#8b1e2d;--wine-soft:#a63a46;--ink:#2a181a;--muted:#6e5356;--line:#e4d0d2;--paper:#faf6f4;--card:#fff;--ok:#1f7a45;--warn:#9a5b12;--err:#8a1020}
        *{box-sizing:border-box}
        html{font-size:16px}
        body{margin:0;color:var(--ink);background:var(--paper);line-height:1.5;font-family:"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei UI","Noto Sans SC",sans-serif}
        body.gate{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:1.5rem;position:relative}
        body.gate>.langs{position:absolute;top:1rem;right:1rem}
        body.gate main{margin:0;max-width:26rem;width:100%}
        header .tools{display:flex;align-items:center;gap:.75rem;margin-left:auto}
        .langs{display:flex;flex-wrap:wrap;gap:.35rem;align-items:center}
        .langs a{font-size:.75rem;color:var(--muted);text-decoration:none;padding:.15rem .35rem;border-radius:.3rem}
        .langs a:hover{color:var(--wine);background:#f4e8e8}
        .langs a.on{color:var(--wine);font-weight:700;background:#f4e8e8}
        body.gate .kicker{margin:0 0 .35rem;color:var(--wine);font-size:.82rem;font-weight:700;letter-spacing:.08em;text-transform:uppercase}
        body.gate h1{font-size:2.15rem;font-weight:700;letter-spacing:-.03em;margin:0 0 1.15rem}
        body.gate h1:after{width:3.1rem;height:.22rem;margin-top:.55rem}
        body.gate .oidc-primary{margin:0 0 .85rem}
        body.gate a.sign-in{display:inline-flex;align-items:center;justify-content:center;width:100%;padding:.72rem 1rem;border-radius:.45rem;background:var(--wine);color:#fff;font-weight:650;text-decoration:none;box-sizing:border-box}
        body.gate a.sign-in:hover{background:var(--wine-deep)}
        body.gate a.sign-in.outline{background:transparent;color:var(--wine);border:1px solid var(--line)}
        body.gate a.sign-in.outline:hover{background:#f8efef;text-decoration:none}
        body.gate .login-or{display:flex;align-items:center;gap:.75rem;margin:1.1rem 0 .85rem;color:var(--muted);font-size:.78rem;font-weight:650;letter-spacing:.04em;text-transform:uppercase}
        body.gate .login-or:before,body.gate .login-or:after{content:\"\";flex:1;height:1px;background:var(--line)}
        body.gate .login-or span{flex:0 0 auto}
        .row{display:flex;align-items:baseline;justify-content:space-between;gap:.75rem}
        .row label{margin:.75rem 0 .2rem}
        .forgot{font-size:.82rem;font-weight:500}
        .secret{display:flex;gap:.4rem;align-items:stretch}
        .secret input{flex:1}
        button.eye{display:flex;align-items:center;justify-content:center;width:2.55rem;padding:0;background:#fff;color:var(--wine);border:1px solid var(--line)}
        button.eye svg{width:1.15rem;height:1.15rem;stroke:currentColor;fill:none;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
        button.eye .off{display:none}
        button.eye.is-shown .on{display:none}
        button.eye.is-shown .off{display:block}
        button.eye:hover{background:#f4e8e8}
        dialog.notice{border:none;padding:0;max-width:22rem;width:calc(100% - 2rem);border-radius:.65rem;box-shadow:0 18px 48px rgba(61,12,18,.28);color:var(--ink);background:var(--card)}
        dialog.notice::backdrop{background:rgba(61,12,18,.48)}
        dialog.notice form{padding:1.2rem 1.35rem 1.15rem}
        dialog.notice h2{margin:.1rem 0 .55rem;font-size:1.05rem}
        dialog.notice p{margin:0;color:var(--muted)}
        dialog.notice button{margin-top:1rem}
        dialog.msgbox{border:none;padding:0;max-width:26rem;width:calc(100% - 2rem);border-radius:.75rem;box-shadow:0 22px 56px rgba(61,12,18,.32);color:var(--ink);background:var(--card)}
        dialog.msgbox::backdrop{background:rgba(36,20,22,.55);backdrop-filter:blur(2px)}
        dialog.msgbox .sheet{padding:1.35rem 1.45rem 1.2rem}
        dialog.msgbox .icon{width:2.4rem;height:2.4rem;border-radius:999px;display:grid;place-items:center;margin:0 0 .85rem;font-size:1.15rem;font-weight:700;background:#f3e8e8;color:var(--wine);border:1px solid #e2b4bb}
        dialog.msgbox.danger .icon{background:#f8e8ea;color:#8a1020;border-color:#e2b4bb}
        dialog.msgbox h2{margin:0 0 .45rem;font-size:1.12rem;letter-spacing:-.01em}
        dialog.msgbox p{margin:0;color:var(--muted);line-height:1.45;font-size:.95rem}
        dialog.msgbox .actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:.5rem;margin-top:1.25rem}
        dialog.msgbox .actions button{min-width:5.5rem;margin:0}
        dialog.msgbox .actions .ghost{background:#fff;color:var(--wine-deep);border:1px solid var(--line)}
        dialog.msgbox .actions .ghost:hover{background:#f4e8e8}
        dialog.msgbox .actions .danger{background:#8a1020;border-color:#6e0c18}
        dialog.msgbox .actions .danger:hover{background:#6e0c18}
        form.login button[type=submit]{width:100%;margin-top:.95rem}
        .alt{margin-top:1.2rem;padding-top:1rem;border-top:1px solid var(--line);color:var(--muted);font-size:.9rem;line-height:1.45}
        .alt .sign-in{margin-top:.55rem}
        header{background:linear-gradient(180deg,var(--wine-deep),var(--wine));color:#fff;box-shadow:0 2px 10px rgba(61,12,18,.28);position:sticky;top:0;z-index:20}
        header .bar{display:flex;flex-wrap:wrap;align-items:center;gap:.55rem 1rem;padding:.7rem 1.4rem;max-width:88rem;margin:0 auto}
        .brand{display:flex;align-items:center;gap:.5rem;color:#fff;text-decoration:none;font-weight:700;letter-spacing:.02em;font-size:1.02rem}
        .brand .mark{width:.72rem;height:.72rem;border-radius:.16rem;background:#e8c4a8;box-shadow:0 0 0 2px rgba(232,196,168,.25)}
        header nav{display:flex;flex-wrap:wrap;gap:.15rem .2rem;align-items:center}
        header nav a{color:#f3ddd9;text-decoration:none;font-size:.86rem;padding:.28rem .55rem;border-radius:.35rem}
        header nav a:hover{background:rgba(255,255,255,.12);color:#fff}
        header .account{display:flex;align-items:center;gap:.5rem}
        header .user{position:relative}
        header .user summary{cursor:pointer;list-style:none;color:#fff;padding:.28rem .65rem;border:1px solid rgba(255,255,255,.35);border-radius:.35rem;font-size:.86rem;background:rgba(255,255,255,.08)}
        header .user summary::-webkit-details-marker{display:none}
        header .user summary::after{content:" \\25BE";font-size:.7rem}
        header .user[open] summary::after{content:" \\25B4"}
        header .user .menu{position:absolute;right:0;top:calc(100% + .5rem);min-width:11.5rem;background:var(--card);color:var(--ink);border:1px solid var(--line);border-radius:.5rem;box-shadow:0 12px 32px rgba(61,12,18,.22);padding:.35rem;z-index:30}
        header .user .menu a,header .user .menu button{display:block;width:100%;text-align:left;font:inherit;color:var(--ink);background:none;border:none;padding:.48rem .7rem;border-radius:.35rem;cursor:pointer;text-decoration:none}
        header .user .menu a:hover,header .user .menu button:hover{background:#f4e8e8;color:var(--wine-deep)}
        header .user .menu .signout{color:var(--wine);border-top:1px solid var(--line);margin-top:.25rem;border-radius:0 0 .35rem .35rem}
        dialog.profile{border:none;padding:0;max-width:44rem;width:calc(100% - 2rem);max-height:calc(100vh - 2.5rem);overflow:auto;border-radius:.65rem;box-shadow:0 18px 48px rgba(61,12,18,.28);color:var(--ink);background:var(--card)}
        dialog.profile::backdrop{background:rgba(61,12,18,.48)}
        dialog.profile .sheet{padding:1.2rem 1.4rem 1.15rem}
        dialog.profile h2{margin:.1rem 0 .85rem}
        dialog.profile dl{margin:0}
        dialog.profile dt{font-size:.72rem;color:var(--muted);margin-top:.55rem;letter-spacing:.04em;text-transform:uppercase}
        dialog.profile dt:first-child{margin-top:0}
        dialog.profile dd{margin:0;font-size:.95rem;word-break:break-all}
        dialog.profile .token-tabs{margin-top:1rem}
        dialog.profile .token-tabs .tab-list{padding:.35rem;border-radius:.45rem .45rem 0 0}
        dialog.profile .token-tabs .tab-panel{display:none;padding:.65rem .7rem .75rem;border:1px solid var(--line);border-top:none;border-radius:0 0 .45rem .45rem}
        dialog.profile .token-tabs .tab-panel.is-active{display:block}
        dialog.profile pre{margin:0;padding:.7rem .8rem;background:#f7f1ee;border:1px solid var(--line);border-radius:.4rem;font-size:.78rem;line-height:1.35;overflow:auto;max-height:16rem;white-space:pre-wrap;word-break:break-word}
        dialog.profile .close{margin-top:1.1rem}
        dialog.form-dialog{border:none;padding:0;max-width:36rem;width:calc(100% - 2rem);max-height:calc(100vh - 2.5rem);overflow:auto;border-radius:.75rem;box-shadow:0 22px 56px rgba(61,12,18,.32);color:var(--ink);background:var(--card)}
        dialog.form-dialog::backdrop{background:rgba(36,20,22,.55);backdrop-filter:blur(2px)}
        dialog.form-dialog .sheet{padding:1.35rem 1.45rem 1.25rem}
        dialog.form-dialog h2{margin:.1rem 0 .35rem;font-size:1.15rem;letter-spacing:-.01em}
        dialog.form-dialog .lead{margin:0 0 1rem}
        dialog.form-dialog .actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:.5rem;margin-top:1.25rem}
        dialog.form-dialog .actions button{min-width:5.5rem;margin:0}
        dialog.form-dialog .actions .ghost{background:#fff;color:var(--wine-deep);border:1px solid var(--line)}
        dialog.form-dialog .actions .ghost:hover{background:#f4e8e8}
        .btn-row{display:flex;flex-wrap:wrap;gap:.5rem;margin:.85rem 0 0}
        .btn-row button.secondary,.btn-row button[data-open-dialog]{background:#fff;color:var(--wine);border:1px solid var(--line);font-weight:600}
        .btn-row button.secondary:hover,.btn-row button[data-open-dialog]:hover{background:#f4e8e8}
        .filter-bar{margin:0 0 1.1rem;padding:1rem 1.1rem;border:1px solid var(--line);border-radius:.5rem;background:#faf7f5}
        .grid-5{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:.65rem .75rem}
        @media (max-width:960px){.grid-5{grid-template-columns:1fr 1fr}}
        @media (max-width:560px){.grid-5{grid-template-columns:1fr}}
        button.linkish{background:none;border:none;color:var(--wine);padding:0;font:inherit;font-weight:600;cursor:pointer;text-decoration:underline;text-underline-offset:.12em}
        button.linkish:hover{color:var(--wine-deep)}
        .audit-dl{margin:0;display:grid;grid-template-columns:10rem 1fr;gap:.35rem .75rem}
        .audit-dl dt{margin:0;color:var(--muted);font-size:.78rem;text-transform:uppercase;letter-spacing:.04em;padding-top:.2rem}
        .audit-dl dd{margin:0;word-break:break-word}
        pre.audit-data{margin:0;padding:.65rem .75rem;background:#f7f1ee;border:1px solid var(--line);border-radius:.4rem;font-size:.78rem;line-height:1.35;overflow:auto;max-height:14rem;white-space:pre-wrap;word-break:break-word}
        code.grant-data{font-size:.75rem;word-break:break-all}
        main{margin:1.4rem auto 2.5rem;max-width:72rem;padding:1.35rem 1.5rem 2rem;background:var(--card);border:1px solid var(--line);border-radius:.65rem;box-shadow:0 8px 28px rgba(61,12,18,.06)}
        main:has(.editor){max-width:88rem}
        .crumbs{margin:0 0 .35rem;color:var(--muted);font-size:.9rem}
        .crumbs a{color:var(--wine-mid);text-decoration:none}
        .page-head{display:flex;flex-wrap:wrap;align-items:flex-start;justify-content:space-between;gap:.75rem 1rem;margin:0 0 .35rem}
        .page-head h1{margin:.1rem 0 0}
        .page-actions{display:flex;flex-wrap:wrap;gap:.45rem;align-items:center;margin-top:.15rem}
        .page-actions form{margin:0}
        .page-actions button.danger{background:#8a1020;color:#fff;border:1px solid #6e0c18;padding:.42rem .85rem;border-radius:.4rem;font:inherit;font-weight:600;cursor:pointer}
        .page-actions button.danger:hover{background:#6e0c18}
        .meta-line{margin:0 0 .85rem;font-size:.9rem;color:var(--muted);display:flex;flex-wrap:wrap;align-items:baseline;gap:.35rem .5rem}
        .meta-line .meta-label{font-weight:600;color:var(--ink);text-transform:uppercase;font-size:.72rem;letter-spacing:.04em}
        .meta-line .meta-value{user-select:all;word-break:break-all;color:var(--ink);font-size:.88rem}
        .password-card{max-width:28rem}
        .password-card .field{margin:0 0 .85rem}
        .password-card input[readonly]{background:#f7f1ee;color:var(--ink)}
        .password-card .form-actions{padding-left:0;padding-right:0;border-top:none;background:transparent}
        .claim-add{margin:0 0 .25rem}
        .claim-add .claim-suggest{margin:0 0 .85rem}
        .claim-add .claim-fields{margin:0}
        .claim-suggest-label{display:block;font-size:.72rem;font-weight:700;color:var(--muted);letter-spacing:.04em;text-transform:uppercase;margin:0 0 .35rem}
        .claim-chips{display:flex;flex-wrap:wrap;gap:.35rem}
        .claim-chips.more-chips{margin-top:.35rem}
        .claim-chips .chip-btn{appearance:none;border:1px solid var(--line);border-radius:999px;background:#fbf6f5;color:var(--ink);font:inherit;font-size:.78rem;font-weight:600;padding:.22rem .65rem;cursor:pointer;line-height:1.2}
        .claim-chips .chip-btn:hover{border-color:#d4b0b4;background:#f4e8e8;color:var(--wine-deep)}
        .claim-chips .chip-btn.is-active{border-color:var(--wine);background:var(--wine);color:#fff}
        .claim-chips .chip-btn.more{border-style:dashed;color:var(--wine-mid)}
        .muted{color:var(--muted)}
        a.button{display:inline-block;background:var(--wine);color:#fff;text-decoration:none;padding:.45rem .9rem;border-radius:.4rem;font-weight:600}
        a.button:hover{background:var(--wine-deep);color:#fff}
        button.ghost{background:#fff;color:var(--wine-deep);border:1px solid var(--line);padding:.28rem .65rem;border-radius:.35rem;cursor:pointer;font:inherit}
        button.ghost:hover{background:#f4e8e8}
        h1{font-size:1.55rem;font-weight:650;margin:.1rem 0 .7rem;letter-spacing:-.02em}
        h1:after{content:"";display:block;width:2.4rem;height:.18rem;margin-top:.45rem;background:var(--wine-mid);border-radius:2px}
        h2{font-size:1.08rem;margin:1.4rem 0 .4rem;color:var(--wine)}
        a{color:var(--wine-mid)}
        a:hover{color:var(--wine-deep)}
        table{border-collapse:collapse;width:100%;margin:.9rem 0;font-size:.92rem}
        th,td{border-bottom:1px solid var(--line);padding:.5rem .65rem;text-align:left}
        th{background:var(--wine);color:#fff;font-weight:600;border-bottom:none}
        tr:nth-child(even) td{background:#fbf6f5}
        tr:hover td{background:#f4e8e8}
        label{display:block;margin:.75rem 0 .2rem;font-size:.86rem;color:var(--muted);font-weight:600}
        input[type=text],input[type=password],input[type=email],input[type=url],input[type=number],textarea,select{width:100%;border:1px solid var(--line);border-radius:.4rem;padding:.45rem .6rem;font:inherit;background:#fff}
        input:focus,textarea:focus,select:focus{outline:2px solid rgba(139,30,45,.28);border-color:var(--wine-mid)}
        textarea{min-height:5.5rem;font-family:ui-monospace,Consolas,monospace}
        .ok{color:var(--ok)}.warn{color:var(--warn)}.err{color:var(--err)}
        .flash{background:#f8efe6;border:1px solid #ead3b8;color:#6a4a22;padding:.65rem .85rem;margin:0 0 1rem;border-radius:.4rem}
        code{background:#f3e8e8;padding:.08rem .35rem;border-radius:.25rem;font-size:.88em}
        .chips{display:flex;flex-wrap:wrap;gap:.4rem;margin:.75rem 0 1rem}
        .chip{display:inline-flex;align-items:center;padding:.18rem .55rem;border-radius:999px;background:#f3e8e8;color:var(--wine-deep);font-size:.78rem;border:1px solid var(--line)}
        .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(11rem,1fr));gap:.7rem;margin:1rem 0 1.4rem}
        .stat{border:1px solid var(--line);border-radius:.5rem;padding:.85rem 1rem;background:#fff;text-decoration:none;color:inherit;display:block}
        .lead{color:var(--muted);max-width:46rem}
        a.sign-in{display:inline-block;padding:.45rem .95rem;border-radius:.4rem;background:var(--wine);color:#fff;text-decoration:none}
        a.sign-in:hover{background:var(--wine-mid);color:#fff}
        .stat strong{display:block;font-size:1.35rem;color:var(--wine)}
        .stat span{color:var(--muted);font-size:.82rem}
        .guide{margin-top:1.6rem;padding-top:.4rem;border-top:1px solid var(--line)}
        .guide h2{font-size:1.05rem}
        .guide ul{padding-left:1.15rem}
        .guide li{margin:.32rem 0}
        .search{display:flex;gap:.45rem;align-items:center;margin:0 0 1rem}
        .search input[type=text]{width:18rem;max-width:100%}
        .pager{display:flex;align-items:center;gap:.75rem;color:var(--muted);font-size:.9rem}
        .actions a,.actions button{margin-right:.45rem}
        button,input[type=submit]{font:inherit;padding:.42rem .85rem;border-radius:.4rem;border:1px solid var(--wine);background:var(--wine);color:#fff;cursor:pointer}
        button:hover,input[type=submit]:hover{background:var(--wine-mid)}
        .actions a{display:inline-block;padding:.35rem .7rem;border-radius:.35rem;border:1px solid var(--line);text-decoration:none;color:var(--wine-deep);background:#fff}
        .editor{display:flex;flex-direction:column;gap:1rem;margin-top:.35rem}
        .editor .lead{margin:0 0 .15rem}
        .template-bar{display:flex;flex-wrap:wrap;align-items:center;gap:.45rem .65rem;margin:0 0 .85rem;padding:.55rem .75rem;border:1px solid var(--line);border-radius:.5rem;background:#f7f0ef}
        .template-bar .label{font-size:.78rem;font-weight:700;color:var(--muted);letter-spacing:.04em;text-transform:uppercase;margin-right:.15rem}
        .templates{display:flex;flex-wrap:wrap;gap:.35rem;flex:1;min-width:12rem}
        .templates .card{appearance:none;border:1px solid var(--line);border-radius:.35rem;background:#fff;color:var(--ink);font:inherit;font-size:.82rem;font-weight:600;padding:.28rem .65rem;cursor:pointer;line-height:1.2}
        .templates .card:hover{border-color:#d4b0b4;background:#f4e8e8;color:var(--wine-deep)}
        .templates .card.is-active{border-color:var(--wine);background:var(--wine);color:#fff}
        .template-bar .hint{flex:1 1 100%;margin:0;font-size:.75rem;color:var(--muted);font-weight:500}
        .tabs{border:1px solid var(--line);border-radius:.55rem;background:#fff;overflow:hidden}
        .tab-list{display:flex;flex-wrap:wrap;gap:.15rem;padding:.45rem .5rem;background:#f7f0ef;border-bottom:1px solid var(--line)}
        .tab-list button{appearance:none;border:1px solid transparent;background:transparent;color:var(--muted);font:inherit;font-size:.86rem;font-weight:600;padding:.42rem .75rem;border-radius:.4rem;cursor:pointer}
        .tab-list button:hover{color:var(--wine-deep);background:rgba(255,255,255,.7)}
        .tab-list button[aria-selected=true]{color:#fff;background:var(--wine);border-color:var(--wine)}
        .tab-panel{display:none;padding:1rem 1.15rem 1.15rem}
        .tab-panel.is-active{display:block}
        .tab-panel>h2{margin:0 0 .65rem;font-size:.98rem;padding-bottom:.45rem;border-bottom:1px solid var(--line)}
        .panel{border:1px solid var(--line);border-radius:.55rem;background:#fff;padding:1rem 1.15rem 1.1rem}
        .panel h2{margin:0 0 .65rem;font-size:.98rem;padding-bottom:.45rem;border-bottom:1px solid var(--line)}
        .panel .hint{display:block;margin:.15rem 0 0;font-size:.78rem;font-weight:500;color:var(--muted)}
        .grid-2{display:grid;grid-template-columns:1fr 1fr;gap:.65rem 1rem}
        .checks{display:grid;grid-template-columns:repeat(auto-fill,minmax(14.5rem,1fr));gap:.35rem .75rem;margin:.15rem 0}
        .checks label{display:flex;align-items:flex-start;gap:.45rem;margin:0;font-weight:500;color:var(--ink);font-size:.9rem;line-height:1.35}
        .checks input{width:auto;margin:.2rem 0 0}
        .checks .sub{display:block;font-size:.75rem;color:var(--muted);font-weight:500}
        .field{margin:0}
        .field>label{margin:.15rem 0 .25rem}
        .duration{border:1px solid var(--line);border-radius:.45rem;padding:.55rem .65rem;background:#fbf8f7}
        .duration .parts{display:flex;flex-wrap:wrap;gap:.4rem .55rem;align-items:flex-end}
        .duration .parts label{margin:0;font-size:.72rem;font-weight:600;color:var(--muted)}
        .duration .parts input{width:4.2rem;padding:.3rem .4rem}
        .duration .total{margin-top:.35rem;font-size:.78rem;color:var(--muted);font-variant-numeric:tabular-nums}
        .list-picker .presets{display:flex;flex-wrap:wrap;gap:.35rem .5rem;margin:0 0 .55rem}
        .list-picker .presets label{display:inline-flex;align-items:center;gap:.35rem;margin:0;padding:.22rem .55rem;border:1px solid var(--line);border-radius:999px;background:#fbf6f5;font-size:.8rem;font-weight:550;color:var(--ink);cursor:pointer}
        .list-picker .presets label:has(input:checked){background:#f3e8e8;border-color:#d4b0b4;color:var(--wine-deep)}
        .list-picker .presets input{width:auto;margin:0}
        .list-picker textarea{min-height:4.5rem}
        .form-actions{display:flex;flex-wrap:wrap;gap:.55rem;align-items:center;padding:.85rem 1.15rem 1rem;margin:0;border-top:1px solid var(--line);background:#fff}
        .form-actions[hidden]{display:none!important}
        .tabs form{margin:0}
        .form-actions .ghost{background:#fff;color:var(--wine-deep);border-color:var(--line)}
        .form-actions .ghost:hover{background:#f4e8e8}
        .callout{border-radius:.45rem;padding:.7rem .85rem;margin:.55rem 0 .75rem;font-size:.88rem;line-height:1.4}
        .callout.warn{background:#f8efe6;border:1px solid #ead3b8;color:#6a4a22}
        .callout.danger{background:#f8e8ea;border:1px solid #e2b4bb;color:#6a1a28}
        .callout.ok{background:#e8f5ee;border:1px solid #b9dcc8;color:#1f5c38}
        .callout code{user-select:all;word-break:break-all}
        .secret-row{display:flex;gap:.45rem;align-items:stretch}
        .secret-row input{flex:1}
        .secret-row button{white-space:nowrap;background:#fff;color:var(--wine);border:1px solid var(--line)}
        .secret-row button:hover{background:#f4e8e8}
        .secret-panel{margin-top:1.4rem}
        .secret-panel h2{margin-top:0}
        @media (max-width:820px){.grid-2{grid-template-columns:1fr}.checks{grid-template-columns:1fr}}
        @media (max-width:720px){header .tools{margin-left:0;width:100%;justify-content:flex-end;flex-wrap:wrap}header .account{margin-left:0}main{margin:.7rem .6rem 1.5rem;padding:1rem}}
        """;
  }

  static String accountMarkup() {
    Authentication authentication = signedIn();
    StringBuilder html = new StringBuilder("<span class=\"account\">");
    if (authentication != null) {
      String label = displayName(authentication);
      html.append("<details class=\"user\"><summary>").append(esc(label)).append("</summary>");
      html.append("<div class=\"menu\">");
      html.append("<button type=\"button\" data-open-profile>")
          .append(esc(Messages.t("user.info")))
          .append("</button>");
      html.append("<a class=\"signout\" href=\"/logout\">")
          .append(esc(Messages.t("logout")))
          .append("</a>");
      html.append("</div></details>");
    }
    html.append("</span>");
    return html.toString();
  }

  static String profileDialog() {
    Authentication authentication = signedIn();
    if (authentication == null) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    html.append("<dialog class=\"profile\" id=\"user-profile\"><div class=\"sheet\">");
    html.append("<form method=\"dialog\"><h2>")
        .append(esc(Messages.t("user.info")))
        .append("</h2><dl>");
    for (String[] row : profileRows(authentication)) {
      html.append("<dt>").append(esc(row[0])).append("</dt><dd>").append(esc(row[1])).append("</dd>");
    }
    html.append("</dl>");
    html.append(tokenClaimsTabs(idTokenClaims(authentication), accessTokenClaims(authentication)));
    html.append("<button class=\"close\" type=\"submit\">")
        .append(esc(Messages.t("user.close")))
        .append("</button></form>");
    html.append("</div></dialog>");
    return html.toString();
  }

  static String accountScript() {
    return "<script>"
        + "document.addEventListener('click',function(e){document.querySelectorAll('header .user[open]').forEach(function(el){if(!el.contains(e.target))el.removeAttribute('open')});});"
        + "document.addEventListener('keydown',function(e){if(e.key==='Escape')document.querySelectorAll('header .user[open]').forEach(function(el){el.removeAttribute('open')});});"
        + "var dlg=document.getElementById('user-profile');"
        + "document.querySelectorAll('[data-open-profile]').forEach(function(btn){btn.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();var user=btn.closest('details.user');if(user)user.removeAttribute('open');if(dlg&&dlg.showModal)dlg.showModal();});});"
        + "document.querySelectorAll('#user-profile [data-token-tabs]').forEach(function(root){"
        + "var tabs=root.querySelectorAll('[role=tab]');"
        + "var panels=root.querySelectorAll('[data-panel]');"
        + "function activate(id){tabs.forEach(function(tab){tab.setAttribute('aria-selected',tab.getAttribute('data-tab')===id?'true':'false');});"
        + "panels.forEach(function(panel){panel.classList.toggle('is-active',panel.getAttribute('data-panel')===id);});}"
        + "tabs.forEach(function(tab){tab.addEventListener('click',function(e){e.preventDefault();activate(tab.getAttribute('data-tab'));});});"
        + "activate(root.getAttribute('data-initial-tab')||'id-token');"
        + "});"
        + "</script>";
  }

  /**
   * Shared confirm / alert dialog. Prefer {@code data-confirm} on forms or {@code SkorubaMsg.*};
   * never use {@code window.alert}/{@code window.confirm}.
   */
  static String messageBoxDialog() {
    return """
        <dialog class="msgbox" id="msg-box" aria-labelledby="msg-box-title">
          <div class="sheet">
            <div class="icon" data-msg-icon aria-hidden="true">!</div>
            <h2 id="msg-box-title" data-msg-title>Confirm</h2>
            <p data-msg-body></p>
            <div class="actions">
              <button type="button" class="ghost" data-msg-cancel>Cancel</button>
              <button type="button" data-msg-ok>OK</button>
            </div>
          </div>
        </dialog>
        """;
  }

  static String messageBoxScript() {
    return """
        <script>
        (function(){
          var dlg=document.getElementById('msg-box');
          if(!dlg||!dlg.showModal){return;}
          var titleEl=dlg.querySelector('[data-msg-title]');
          var bodyEl=dlg.querySelector('[data-msg-body]');
          var okBtn=dlg.querySelector('[data-msg-ok]');
          var cancelBtn=dlg.querySelector('[data-msg-cancel]');
          var iconEl=dlg.querySelector('[data-msg-icon]');
          var pending=null;
          function finish(ok){
            if(dlg.open){dlg.close();}
            var resolve=pending;
            pending=null;
            if(resolve){resolve(!!ok);}
          }
          cancelBtn.addEventListener('click',function(){finish(false);});
          okBtn.addEventListener('click',function(){finish(true);});
          dlg.addEventListener('cancel',function(e){e.preventDefault();finish(false);});
          function openBox(opts){
            opts=opts||{};
            var isConfirm=!!opts.confirm;
            var danger=!!opts.danger;
            titleEl.textContent=opts.title||(isConfirm?'Confirm':'Notice');
            bodyEl.textContent=opts.message||'';
            okBtn.textContent=opts.okLabel||(isConfirm?'Delete':'OK');
            cancelBtn.hidden=!isConfirm;
            cancelBtn.textContent=opts.cancelLabel||'Cancel';
            okBtn.classList.toggle('danger',danger);
            dlg.classList.toggle('danger',danger);
            if(iconEl){iconEl.textContent=danger?'!':'i';}
            return new Promise(function(resolve){
              pending=resolve;
              dlg.showModal();
              (isConfirm?cancelBtn:okBtn).focus();
            });
          }
          window.SkorubaMsg={
            alert:function(message,opts){
              opts=opts||{};
              return openBox({
                title:opts.title||'Notice',
                message:message==null?'':String(message),
                okLabel:opts.okLabel||'OK',
                confirm:false,
                danger:!!opts.danger
              });
            },
            confirm:function(message,opts){
              opts=opts||{};
              return openBox({
                title:opts.title||'Confirm',
                message:message==null?'':String(message),
                okLabel:opts.okLabel||'Delete',
                cancelLabel:opts.cancelLabel||'Cancel',
                confirm:true,
                danger:opts.danger!==false
              });
            }
          };
          window.alert=function(message){window.SkorubaMsg.alert(message);};
          window.confirm=function(){
            console.error('window.confirm is disabled; use data-confirm or SkorubaMsg.confirm');
            return false;
          };
          document.addEventListener('submit',function(e){
            var form=e.target;
            if(!form||form.tagName!=='FORM'){return;}
            var msg=form.getAttribute('data-confirm');
            if(!msg||form.getAttribute('data-confirm-ok')==='1'){return;}
            e.preventDefault();
            e.stopPropagation();
            window.SkorubaMsg.confirm(msg,{
              title:form.getAttribute('data-confirm-title')||'Confirm delete',
              okLabel:form.getAttribute('data-confirm-ok-label')||'Delete',
              cancelLabel:form.getAttribute('data-confirm-cancel-label')||'Cancel',
              danger:form.getAttribute('data-confirm-danger')!=='0'
            }).then(function(ok){
              if(!ok){return;}
              form.setAttribute('data-confirm-ok','1');
              if(typeof form.requestSubmit==='function'){form.requestSubmit();}
              else{form.submit();}
            });
          },true);
        })();
        </script>
        """;
  }

  /** Destructive POST form attributes — use instead of {@code onsubmit="return confirm(...)"}. */
  public static String dataConfirm(String message) {
    return dataConfirm(message, "Confirm delete", "Delete", true);
  }

  public static String dataConfirm(String message, String title, String okLabel, boolean danger) {
    StringBuilder attrs = new StringBuilder();
    attrs.append(" data-confirm=\"").append(esc(message)).append('"');
    if (title != null && !title.isBlank()) {
      attrs.append(" data-confirm-title=\"").append(esc(title)).append('"');
    }
    if (okLabel != null && !okLabel.isBlank()) {
      attrs.append(" data-confirm-ok-label=\"").append(esc(okLabel)).append('"');
    }
    if (!danger) {
      attrs.append(" data-confirm-danger=\"0\"");
    }
    return attrs.toString();
  }

  static List<String[]> profileRows(Authentication authentication) {
    Map<String, Object> claims = claims(authentication);
    String username = text(claims.get("preferred_username"));
    String name = text(claims.get("name"));
    String email = text(claims.get("email"));
    String subject = text(claims.get("sub"));
    if (subject == null) {
      subject = authentication.getName();
    }
    List<String[]> rows = new ArrayList<>();
    addRow(rows, "Username", username);
    if (name != null && !name.equals(username)) {
      addRow(rows, Messages.t("user.name"), name);
    }
    addRow(rows, Messages.t("user.email"), email);
    addRow(rows, "Email verified", boolText(claims.get("email_verified")));
    addRow(rows, "Subject", subject);
    addRow(rows, "Issuer", text(claims.get("iss")));
    addRow(rows, "Audience", joinClaim(claims.get("aud")));
    String roles = roles(authentication);
    addRow(rows, Messages.t("user.roles"), roles);
    if (rows.isEmpty()) {
      addRow(rows, "User", authentication.getName());
    }
    return rows;
  }

  static String displayName(Authentication authentication) {
    Map<String, Object> claims = claims(authentication);
    String username = text(claims.get("preferred_username"));
    if (username != null) {
      return username;
    }
    String name = text(claims.get("name"));
    if (name != null) {
      return name;
    }
    String email = text(claims.get("email"));
    if (email != null) {
      return email;
    }
    String subject = text(claims.get("sub"));
    if (subject != null) {
      return subject;
    }
    return authentication.getName();
  }

  static Map<String, Object> claims(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof StsSessionUser sts) {
      return sts.getAttributes();
    }
    if (principal instanceof OidcUser oidc && oidc.getClaims() != null) {
      return oidc.getClaims();
    }
    if (principal instanceof OAuth2User user && user.getAttributes() != null) {
      return user.getAttributes();
    }
    if (principal instanceof AdminIdentityUser local) {
      LinkedHashMap<String, Object> claims = new LinkedHashMap<>();
      if (local.userName() != null && !local.userName().isBlank()) {
        claims.put("preferred_username", local.userName());
      }
      if (local.email() != null && !local.email().isBlank()) {
        claims.put("email", local.email());
      }
      if (local.id() != null && !local.id().isBlank()) {
        claims.put("sub", local.id());
      }
      return claims;
    }
    return Map.of();
  }

  static String roles(Authentication authentication) {
    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
    if (authorities == null || authorities.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (GrantedAuthority authority : authorities) {
      if (authority == null || authority.getAuthority() == null) {
        continue;
      }
      String name = authority.getAuthority();
      if (name.isBlank() || name.startsWith("SCOPE_") || "OIDC_USER".equals(name)) {
        continue;
      }
      names.add(name);
    }
    return names.isEmpty() ? null : String.join(", ", names);
  }

  static void addRow(List<String[]> rows, String label, String value) {
    if (value != null && !value.isBlank()) {
      rows.add(new String[] {label, value});
    }
  }

  static String text(Object value) {
    if (value instanceof String text && !text.isBlank()) {
      return text;
    }
    return null;
  }

  static Map<String, Object> idTokenClaims(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof StsSessionUser sts) {
      return sts.idTokenClaims();
    }
    if (principal instanceof OidcUser oidc && oidc.getIdToken() != null) {
      return oidc.getIdToken().getClaims();
    }
    return Map.of();
  }

  static Map<String, Object> accessTokenClaims(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof StsSessionUser sts) {
      return sts.accessTokenClaims();
    }
    return Map.of();
  }

  static Map<String, Object> userInfoClaims(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof StsSessionUser sts) {
      return sts.userInfo();
    }
    if (principal instanceof OidcUser oidc && oidc.getUserInfo() != null) {
      return oidc.getUserInfo().getClaims();
    }
    return Map.of();
  }

  /** ID token / access token payloads in tabs (UserInfo endpoint body is not shown). */
  static String tokenClaimsTabs(
      Map<String, Object> idTokenClaims, Map<String, Object> accessTokenClaims) {
    String idJson = JwtPayloads.pretty(idTokenClaims);
    String accessJson = JwtPayloads.pretty(accessTokenClaims);
    boolean hasId = idJson != null && !idJson.isBlank();
    boolean hasAccess = accessJson != null && !accessJson.isBlank();
    if (!hasId && !hasAccess) {
      return "";
    }
    String initial = hasId ? "id-token" : "access-token";
    StringBuilder html = new StringBuilder();
    html.append("<div class=\"tabs token-tabs\" data-token-tabs data-initial-tab=\"")
        .append(initial)
        .append("\">");
    html.append("<div class=\"tab-list\" role=\"tablist\" aria-label=\"Token claims\">");
    if (hasId) {
      html.append(
          "<button type=\"button\" role=\"tab\" data-tab=\"id-token\" aria-selected=\"true\">ID token</button>");
    }
    if (hasAccess) {
      html.append(
          "<button type=\"button\" role=\"tab\" data-tab=\"access-token\" aria-selected=\"")
          .append(hasId ? "false" : "true")
          .append("\">Access token</button>");
    }
    html.append("</div>");
    if (hasId) {
      html.append("<div class=\"tab-panel is-active\" data-panel=\"id-token\" role=\"tabpanel\">");
      html.append("<pre>").append(esc(idJson)).append("</pre></div>");
    }
    if (hasAccess) {
      html.append("<div class=\"tab-panel")
          .append(hasId ? "" : " is-active")
          .append("\" data-panel=\"access-token\" role=\"tabpanel\">");
      html.append("<pre>").append(esc(accessJson)).append("</pre></div>");
    }
    html.append("</div>");
    return html.toString();
  }

  static String joinClaim(Object value) {
    if (value instanceof Collection<?> values) {
      List<String> parts = new ArrayList<>();
      for (Object item : values) {
        if (item != null && !item.toString().isBlank()) {
          parts.add(item.toString());
        }
      }
      return parts.isEmpty() ? null : String.join(", ", parts);
    }
    return text(value == null ? null : value.toString());
  }

  static String boolText(Object value) {
    if (value instanceof Boolean flag) {
      return flag ? "true" : "false";
    }
    if (value instanceof String text) {
      if ("true".equalsIgnoreCase(text)) {
        return "true";
      }
      if ("false".equalsIgnoreCase(text)) {
        return "false";
      }
    }
    return null;
  }

  /** Standing explanation of this Admin UI. No tenant names or secrets. */
  public static String guide() {
    StringBuilder html = new StringBuilder("<section class=\"guide\">");
    html.append("<h2>").append(esc(Messages.t("guide.title"))).append("</h2>");
    html.append("<p>").append(esc(Messages.t("guide.p1"))).append("</p>");
    html.append("<p>").append(esc(Messages.t("guide.p2"))).append("</p>");
    html.append("<h2>").append(esc(Messages.t("guide.sections"))).append("</h2><ul>");
    guideItem(html, "/admin", "nav.home", "guide.sec.home");
    guideItem(html, "/admin/clients", "nav.clients", "guide.sec.clients");
    guideItem(html, "/admin/users", "nav.users", "guide.sec.users");
    guideItem(html, "/admin/roles", "nav.roles", "guide.sec.roles");
    guideItem(html, "/admin/api-resources", "nav.apiResources", "guide.sec.apiResources");
    guideItem(html, "/admin/api-scopes", "nav.apiScopes", "guide.sec.apiScopes");
    guideItem(html, "/admin/identity-resources", "nav.identityResources", "guide.sec.identityResources");
    guideItem(html, "/admin/grants", "nav.grants", "guide.sec.grants");
    guideItem(html, "/admin/audit-logs", "nav.auditLogs", "guide.sec.audit");
    guideItem(html, "/about", "nav.about", "guide.sec.about");
    html.append("</ul>");
    html.append("<h2>").append(esc(Messages.t("guide.login.title"))).append("</h2>");
    html.append("<p>").append(esc(Messages.t("guide.login.p1"))).append("</p>");
    html.append("<p>").append(esc(Messages.t("guide.login.p2"))).append("</p>");
    html.append("<h2>").append(esc(Messages.t("guide.db.title"))).append("</h2>");
    html.append("<p>").append(esc(Messages.t("guide.db.body"))).append("</p>");
    html.append("</section>");
    return html.toString();
  }

  private static void guideItem(StringBuilder html, String href, String navKey, String bodyKey) {
    html.append("<li><a href=\"")
        .append(esc(href))
        .append("\">")
        .append(esc(Messages.t(navKey)))
        .append("</a> — ")
        .append(esc(Messages.t(bodyKey)))
        .append("</li>");
  }

  /** Public product copy for the Admin About tab. Locale-specific; no dual-language block. */
  public static String about() {
    StringBuilder html = new StringBuilder();
    html.append("<h1>").append(esc(Messages.t("about.title"))).append("</h1>");
    html.append("<p class=\"lead\">").append(esc(Messages.t("about.lead"))).append("</p>");
    html.append("<h2>").append(esc(Messages.t("about.special.title"))).append("</h2><ul>");
    html.append("<li>").append(esc(Messages.t("about.special.tables"))).append("</li>");
    html.append("<li>").append(esc(Messages.t("about.special.paths"))).append("</li>");
    html.append("<li>").append(esc(Messages.t("about.special.aud"))).append("</li></ul>");
    html.append("<h2>").append(esc(Messages.t("about.protocols.title"))).append("</h2>");
    html.append("<p>").append(esc(Messages.t("about.protocols.y"))).append("</p>");
    html.append("<p>").append(esc(Messages.t("about.protocols.p"))).append("</p>");
    html.append("<h2>").append(esc(Messages.t("about.processes.title"))).append("</h2><ul>");
    html.append("<li>").append(esc(Messages.t("about.processes.sts"))).append("</li>");
    html.append("<li>").append(esc(Messages.t("about.processes.admin"))).append("</li>");
    html.append("<li>").append(esc(Messages.t("about.processes.api"))).append("</li>");
    html.append("<li>").append(esc(Messages.t("about.processes.console"))).append("</li></ul>");
    html.append("<h2>").append(esc(Messages.t("about.contracts.title"))).append("</h2><ul>");
    html.append("<li>").append(esc(Messages.t("about.contracts.paths"))).append("</li>");
    html.append("<li>").append(esc(Messages.t("about.contracts.aud"))).append("</li>");
    html.append("<li>").append(esc(Messages.t("about.contracts.password"))).append("</li>");
    html.append("<li>").append(esc(Messages.t("about.contracts.stack"))).append("</li></ul>");
    html.append("<h2>").append(esc(Messages.t("about.github.title"))).append("</h2>");
    html.append("<p>").append(esc(Messages.t("about.github.body"))).append("</p>");
    return html.toString();
  }

  public static String hiddenCsrf(String name, String token) {
    if (name == null || name.isBlank()) {
      return "";
    }
    return "<input type=\"hidden\" name=\"" + esc(name) + "\" value=\"" + esc(token) + "\">";
  }

  public static String searchBar(String action, String searchText, int page) {
    StringBuilder html = new StringBuilder();
    html.append("<form class=\"search\" method=\"get\" action=\"").append(esc(action)).append("\">");
    html.append("<input type=\"text\" name=\"searchText\" value=\"")
        .append(esc(searchText))
        .append("\" placeholder=\"")
        .append(esc(Messages.t("action.search")))
        .append("\">");
    html.append("<input type=\"hidden\" name=\"page\" value=\"1\">");
    html.append("<button type=\"submit\">")
        .append(esc(Messages.t("action.search")))
        .append("</button></form>");
    if (page > 1) {
      html.append("<p>").append(esc(Messages.t("pager.page", page))).append("</p>");
    }
    return html.toString();
  }

  public static String pager(String path, String searchText, int page, int pageSize, int total) {
    int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
    StringBuilder html = new StringBuilder("<p class=\"pager\">");
    if (page > 1) {
      html.append("<a href=\"")
          .append(esc(path))
          .append("?searchText=")
          .append(esc(searchText))
          .append("&page=")
          .append(page - 1)
          .append("\">")
          .append(esc(Messages.t("action.prev")))
          .append("</a> ");
    }
    html.append(esc(Messages.t("pager.page", page)))
        .append(" / ")
        .append(pages)
        .append(" (")
        .append(total)
        .append(")");
    if (page < pages) {
      html.append(" <a href=\"")
          .append(esc(path))
          .append("?searchText=")
          .append(esc(searchText))
          .append("&page=")
          .append(page + 1)
          .append("\">")
          .append(esc(Messages.t("action.next")))
          .append("</a>");
    }
    html.append("</p>");
    return html.toString();
  }

  static Authentication signedIn() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }
    String name = authentication.getName();
    return name == null || name.isBlank() ? null : authentication;
  }

  public static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  public static String joinLines(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    return String.join("\n", values);
  }

  public static List<String> splitLines(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(raw.split("\\r?\\n"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  /** Split total seconds into days, hours, minutes, seconds (non-negative). */
  public static int[] splitDuration(int totalSeconds) {
    int s = Math.max(0, totalSeconds);
    int days = s / 86_400;
    s %= 86_400;
    int hours = s / 3_600;
    s %= 3_600;
    int minutes = s / 60;
    s %= 60;
    return new int[] {days, hours, minutes, s};
  }

  public static String durationField(String name, String label, int seconds) {
    int[] parts = splitDuration(seconds);
    StringBuilder html = new StringBuilder("<div class=\"field duration\" data-duration>");
    html.append("<label>").append(esc(label)).append("</label>");
    html.append("<input type=\"hidden\" name=\"")
        .append(esc(name))
        .append("\" value=\"")
        .append(seconds)
        .append("\" data-seconds>");
    html.append("<div class=\"parts\">");
    html.append(durationPart("d", "Days", parts[0]));
    html.append(durationPart("h", "Hours", parts[1]));
    html.append(durationPart("m", "Min", parts[2]));
    html.append(durationPart("s", "Sec", parts[3]));
    html.append("</div>");
    html.append("<div class=\"total\" data-total>")
        .append(seconds)
        .append(" seconds</div>");
    html.append("</div>");
    return html.toString();
  }

  private static String durationPart(String unit, String label, int value) {
    return "<label>"
        + esc(label)
        + "<input type=\"number\" min=\"0\" step=\"1\" value=\""
        + value
        + "\" data-unit=\""
        + unit
        + "\"></label>";
  }

  public static String flagCheckbox(String name, String label, String hint, boolean checked) {
    StringBuilder html = new StringBuilder("<label>");
    html.append("<input type=\"checkbox\" name=\"")
        .append(esc(name))
        .append("\"")
        .append(checked ? " checked" : "")
        .append(">");
    html.append("<span>").append(esc(label));
    if (hint != null && !hint.isBlank()) {
      html.append("<span class=\"sub\">").append(esc(hint)).append("</span>");
    }
    html.append("</span></label>");
    return html.toString();
  }

  public static String accessTokenTypeSelect(int value) {
    boolean jwt = value != 1;
    StringBuilder html = new StringBuilder("<div class=\"field\">");
    html.append("<label>Access token type</label>");
    html.append("<select name=\"accessTokenType\">");
    html.append("<option value=\"0\"")
        .append(jwt ? " selected" : "")
        .append(">JWT (self-contained)</option>");
    html.append("<option value=\"1\"")
        .append(jwt ? "" : " selected")
        .append(">Reference (opaque / introspection)</option>");
    html.append("</select>");
    html.append(
        "<span class=\"hint\">Skoruba4j STS issues JWT access tokens; prefer JWT unless you need reference tokens.</span>");
    html.append("</div>");
    return html.toString();
  }

  public static String listPicker(
      String name, String label, String hint, String value, String[] presets) {
    java.util.Set<String> selected = new java.util.LinkedHashSet<>(splitLines(value));
    StringBuilder html = new StringBuilder("<div class=\"field list-picker\" data-list-picker>");
    html.append("<label>").append(esc(label)).append("</label>");
    if (hint != null && !hint.isBlank()) {
      html.append("<span class=\"hint\">").append(esc(hint)).append("</span>");
    }
    html.append("<div class=\"presets\">");
    for (String preset : presets) {
      boolean on = selected.contains(preset);
      html.append("<label><input type=\"checkbox\" data-preset value=\"")
          .append(esc(preset))
          .append("\"")
          .append(on ? " checked" : "")
          .append("> ")
          .append(esc(preset))
          .append("</label>");
    }
    html.append("</div>");
    html.append("<textarea name=\"")
        .append(esc(name))
        .append("\" rows=\"4\" spellcheck=\"false\">")
        .append(esc(value))
        .append("</textarea>");
    html.append("</div>");
    return html.toString();
  }

  public static String clientEditorScript() {
    return """
        <script>
        (function(){
          function num(el){var n=parseInt(el&&el.value,10);return isNaN(n)||n<0?0:n;}
          function syncDuration(root){
            var sec=num(root.querySelector('[data-unit=s]'))
              +num(root.querySelector('[data-unit=m]'))*60
              +num(root.querySelector('[data-unit=h]'))*3600
              +num(root.querySelector('[data-unit=d]'))*86400;
            var hidden=root.querySelector('[data-seconds]');
            var total=root.querySelector('[data-total]');
            if(hidden) hidden.value=String(sec);
            if(total) total.textContent=sec+' seconds';
          }
          document.querySelectorAll('[data-duration]').forEach(function(root){
            root.querySelectorAll('input[data-unit]').forEach(function(input){
              input.addEventListener('input',function(){syncDuration(root);});
              input.addEventListener('change',function(){syncDuration(root);});
            });
            syncDuration(root);
          });
          function linesOf(ta){
            return ta.value.split(/\\r?\\n/).map(function(s){return s.trim();}).filter(Boolean);
          }
          function writeLines(ta, lines){
            var seen={}; var out=[];
            lines.forEach(function(line){
              if(!line||seen[line]) return;
              seen[line]=1; out.push(line);
            });
            ta.value=out.join('\\n');
          }
          function syncListPicker(root){
            var ta=root.querySelector('textarea');
            if(!ta) return;
            var set={};
            linesOf(ta).forEach(function(x){set[x]=1;});
            root.querySelectorAll('input[data-preset]').forEach(function(box){
              box.checked=!!set[box.value];
            });
          }
          document.querySelectorAll('[data-list-picker]').forEach(function(root){
            var ta=root.querySelector('textarea');
            if(!ta) return;
            root.querySelectorAll('input[data-preset]').forEach(function(box){
              box.addEventListener('change',function(){
                var lines=linesOf(ta);
                var v=box.value;
                if(box.checked){
                  if(lines.indexOf(v)<0) lines.push(v);
                }else{
                  lines=lines.filter(function(x){return x!==v;});
                }
                writeLines(ta, lines);
              });
            });
            ta.addEventListener('input',function(){syncListPicker(root);});
          });
          function setCheck(form, name, on){
            var el=form.querySelector('input[type=checkbox][name=\"'+name+'\"]');
            if(el) el.checked=!!on;
          }
          function setTextarea(form, name, value){
            var el=form.querySelector('textarea[name=\"'+name+'\"]');
            if(!el) return;
            el.value=value||'';
            var picker=el.closest('[data-list-picker]');
            if(picker) syncListPicker(picker);
          }
          function findClientForm(from){
            var root=from&&from.closest?from.closest('.editor'):null;
            if(root){
              var f=root.querySelector('form[data-client-settings],form');
              if(f) return f;
            }
            return document.querySelector('.editor form[data-client-settings],.editor form');
          }
          function activateClientTab(id){
            document.querySelectorAll('[data-tabs]').forEach(function(root){
              if(typeof root._activateTab==='function') root._activateTab(id);
            });
          }
          document.querySelectorAll('[data-client-templates] .card').forEach(function(card){
            card.addEventListener('click',function(ev){
              ev.preventDefault();
              var form=findClientForm(card);
              if(!form) return;
              setTextarea(form,'grantTypes',card.getAttribute('data-grants')||'');
              setTextarea(form,'scopes',card.getAttribute('data-scopes')||'');
              setCheck(form,'requireClientSecret',card.getAttribute('data-secret')==='1');
              setCheck(form,'requirePkce',card.getAttribute('data-pkce')==='1');
              setCheck(form,'allowOfflineAccess',card.getAttribute('data-offline')==='1');
              document.querySelectorAll('[data-client-templates] .card').forEach(function(c){
                c.classList.toggle('is-active',c===card);
              });
              var summary=card.closest('.template-bar');
              summary=summary&&summary.querySelector('[data-template-summary]');
              if(summary){
                var title=card.getAttribute('data-title')||'';
                var sub=card.getAttribute('data-subtitle')||'';
                summary.textContent=title+(sub?' — '+sub:'');
              }
              syncDeviceGrant(form);
              // Name tab looks the same for every template; show Basic where presets land.
              activateClientTab('basic');
              try{
                var url=new URL(window.location.href);
                url.searchParams.set('template',card.getAttribute('data-template')||'empty');
                url.searchParams.set('tab','basic');
                history.replaceState(null,'',url.toString());
              }catch(e){}
            });
          });
          var DEVICE_GRANT='urn:ietf:params:oauth:grant-type:device_code';
          function syncDeviceGrant(form){
            var box=form.querySelector('[data-device-grant]');
            var ta=form.querySelector('textarea[name=\"grantTypes\"]');
            if(!box||!ta) return;
            box.checked=linesOf(ta).indexOf(DEVICE_GRANT)>=0;
          }
          document.querySelectorAll('.editor form').forEach(function(form){
            var box=form.querySelector('[data-device-grant]');
            var ta=form.querySelector('textarea[name=\"grantTypes\"]');
            if(box&&ta){
              syncDeviceGrant(form);
              box.addEventListener('change',function(){
                var lines=linesOf(ta);
                if(box.checked){
                  if(lines.indexOf(DEVICE_GRANT)<0) lines.push(DEVICE_GRANT);
                }else{
                  lines=lines.filter(function(x){return x!==DEVICE_GRANT;});
                }
                writeLines(ta, lines);
                var picker=ta.closest('[data-list-picker]');
                if(picker) syncListPicker(picker);
              });
              ta.addEventListener('input',function(){syncDeviceGrant(form);});
            }
          });
          document.querySelectorAll('[data-tabs]').forEach(function(root){
            var tabs=root.querySelectorAll('[role=tab]');
            var panels=root.querySelectorAll('[data-panel]');
            var settingsActions=root.querySelector('[data-settings-actions]');
            function activate(id){
              tabs.forEach(function(tab){
                var on=tab.getAttribute('data-tab')===id;
                tab.setAttribute('aria-selected',on?'true':'false');
              });
              panels.forEach(function(panel){
                panel.classList.toggle('is-active',panel.getAttribute('data-panel')===id);
              });
              if(settingsActions){
                settingsActions.hidden=(id==='secrets'||id==='claims'||id==='properties');
              }
              try{
                var url=new URL(window.location.href);
                if(id&&id!=='name'){url.searchParams.set('tab',id);}
                else{url.searchParams.delete('tab');}
                history.replaceState(null,'',url.toString());
              }catch(e){}
            }
            root._activateTab=activate;
            tabs.forEach(function(tab){
              tab.addEventListener('click',function(){activate(tab.getAttribute('data-tab'));});
            });
            var initial=root.getAttribute('data-initial-tab')||'name';
            try{
              var q=new URL(window.location.href).searchParams.get('tab');
              if(q) initial=q;
            }catch(e){}
            activate(initial);
          });
        })();
        </script>
        """;
  }

  public static String clientTemplatePicker(ClientCreateTemplate selected) {
    ClientCreateTemplate active = selected == null ? ClientCreateTemplate.EMPTY : selected;
    StringBuilder html = new StringBuilder();
    html.append("<div class=\"template-bar\">");
    html.append("<span class=\"label\">Template</span>");
    html.append("<div class=\"templates\" data-client-templates>");
    for (ClientCreateTemplate t : ClientCreateTemplate.values()) {
      boolean on = t == active;
      String tip = t.title() + " — " + t.subtitle();
      html.append("<button type=\"button\" class=\"card")
          .append(on ? " is-active" : "")
          .append("\" title=\"")
          .append(esc(tip))
          .append("\" data-template=\"")
          .append(esc(t.id()))
          .append("\" data-title=\"")
          .append(esc(t.title()))
          .append("\" data-subtitle=\"")
          .append(esc(t.subtitle()))
          .append("\" data-grants=\"")
          .append(esc(t.grantTypes()))
          .append("\" data-scopes=\"")
          .append(esc(t.scopes()))
          .append("\" data-secret=\"")
          .append(t.requireClientSecret() ? "1" : "0")
          .append("\" data-pkce=\"")
          .append(t.requirePkce() ? "1" : "0")
          .append("\" data-offline=\"")
          .append(t.allowOfflineAccess() ? "1" : "0")
          .append("\">")
          .append(esc(t.shortLabel()))
          .append("</button>");
    }
    html.append("</div>");
    html.append("<p class=\"hint\" data-template-summary>")
        .append(esc(active.title() + " — " + active.subtitle()))
        .append("</p>");
    html.append("</div>");
    return html.toString();
  }
}
