package com.myano.skoruba4j.adminapi.web.ui;

import com.myano.skoruba4j.adminapi.config.ApiLoginMode;
import com.myano.skoruba4j.adminapi.config.IdserverProperties;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginClientSettings;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@ConditionalOnProperty(
    prefix = "idserver.admin",
    name = "api-ui-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class UiLoginController {
  private final IdserverProperties props;
  private final Optional<JdbcRepositories> jdbc;

  public UiLoginController(IdserverProperties props, Optional<JdbcRepositories> jdbc) {
    this.props = props;
    this.jdbc = jdbc;
  }

  /** STS post-logout redirect (see {@code ApiUiLogout}). */
  @GetMapping("/signout-callback-oidc")
  public RedirectView signedOut() {
    RedirectView login = new RedirectView("/login");
    login.setContextRelative(true);
    return login;
  }

  @GetMapping(value = "/login", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String login(
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "error_description", required = false) String errorDescription,
      @RequestParam(value = "denied", required = false) String denied) {
    ApiLoginMode mode = props.apiLoginMode();
    String flash = null;
    if (denied != null) {
      flash = LoginFlash.adminAccessDenied();
    } else if (error != null) {
      flash = LoginFlash.forQuery(error, errorDescription, props.adminRole());
    }
    ExternalLoginClientSettings clientExt = clientExternalSettings();
    boolean google = mode.usesStsOidc() && props.googleLoginConfigured() && clientExt.googleEnabled();
    boolean microsoft =
        mode.usesStsOidc() && props.microsoftLoginConfigured() && clientExt.microsoftEnabled();
    boolean whatsapp =
        mode.usesStsOidc() && clientExt.whatsappEnabled() && props.whatsappLoginConfigured();
    boolean wechat =
        mode.usesStsOidc() && clientExt.wechatEnabled() && props.wechatLoginConfigured();
    boolean showSts = mode.usesStsOidc();
    boolean anyExternal = google || microsoft || whatsapp || wechat || showSts;

    StringBuilder body = new StringBuilder();
    body.append("<div class=\"card login-card\">");
    if (flash != null && !flash.isBlank()) {
      body.append("<p class=\"flash\">").append(ApiUiHtml.esc(flash)).append("</p>");
    }
    body.append("<p class=\"kicker\">Skoruba4j Admin API</p>");
    body.append("<h1>Sign in</h1>");
    body.append("<p class=\"muted lead\">Identity user with role <code>")
        .append(ApiUiHtml.esc(props.adminRole()))
        .append("</code>.</p>");
    if (mode.showsPasswordForm()) {
      body.append("<form method=\"post\" action=\"/login\">");
      body.append("<label for=\"username\">Username or email</label>");
      body.append(
          "<input id=\"username\" name=\"username\" type=\"text\" autocomplete=\"username\" required autofocus>");
      body.append("<label for=\"password\">Password</label>");
      body.append(
          "<input id=\"password\" name=\"password\" type=\"password\""
              + " autocomplete=\"current-password\" required>");
      body.append("<button type=\"submit\">Sign in</button>");
      body.append("</form>");
    }
    if (anyExternal) {
      if (mode.showsPasswordForm()) {
        body.append("<p class=\"login-or\"><span>or</span></p>");
      }
      if (google) {
        body.append(stsIdpButton("google", "Sign in with Google"));
      }
      if (microsoft) {
        body.append(stsIdpButton("microsoft", "Sign in with Microsoft"));
      }
      if (whatsapp) {
        body.append(stsIdpButton("whatsapp", "Sign in with WhatsApp"));
      }
      if (wechat) {
        body.append(stsIdpButton("wechat", "Sign in with WeChat"));
      }
      if (showSts) {
        body.append(
            "<p class=\"oidc-primary\"><a class=\"sign-in\" href=\"/oauth2/authorization/sts\">"
                + "Sign in with STS</a></p>");
      }
    }
    body.append("</div>");
    return ApiUiHtml.loginPage(body.toString());
  }

  private ExternalLoginClientSettings clientExternalSettings() {
    if (jdbc.isEmpty()) {
      return ExternalLoginClientSettings.disabled();
    }
    return jdbc.get()
        .clients()
        .findEnabledByClientId(props.apiClientId())
        .map(ExternalLoginClientSettings::from)
        .orElseGet(ExternalLoginClientSettings::disabled);
  }

  private static String stsIdpButton(String idp, String label) {
    return "<p class=\"oidc-primary\"><a class=\"sign-in\" href=\"/oauth2/authorization/sts?idp="
        + ApiUiHtml.esc(idp)
        + "\">"
        + ApiUiHtml.esc(label)
        + "</a></p>";
  }
}
