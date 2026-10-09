package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.ClientConfiguration;
import com.myano.skoruba4j.domain.configstore.ClientSummary;
import com.myano.skoruba4j.domain.configstore.ClientWrite;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginClientSettings;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginLinkMode;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import com.myano.skoruba4j.domain.password.SharedSecretHasher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class ClientsAdminController {
  private final Optional<JdbcRepositories> jdbc;

  public ClientsAdminController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/admin/clients", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String searchText,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    PageQuery query = PageQuery.of(searchText, page, 20);
    StringBuilder body = new StringBuilder("<h1>").append(AdminHtml.esc(Messages.t("clients.title"))).append("</h1>");
    body.append("<p><a href=\"/admin/clients/new\">New client</a></p>");
    body.append(AdminHtml.searchBar("/admin/clients", query.searchText(), query.page()));
    JdbcRepositories repos = repos();
    PageResult<ClientSummary> result = repos.clients().search(query);
    body.append("<table><tr><th>Id</th><th>ClientId</th><th>Name</th><th>Enabled</th></tr>");
    for (ClientSummary c : result.items()) {
      body.append("<tr><td>")
          .append(c.id())
          .append("</td><td><a href=\"/admin/clients/")
          .append(c.id())
          .append("\">")
          .append(AdminHtml.esc(c.clientId()))
          .append("</a></td><td>")
          .append(AdminHtml.esc(c.clientName()))
          .append("</td><td>")
          .append(c.enabled())
          .append("</td></tr>");
    }
    body.append("</table>");
    body.append(AdminHtml.pager("/admin/clients", query.searchText(), result.page(), result.pageSize(), result.totalCount()));
    return page(request, Messages.t("clients.title"), body.toString());
  }

  @GetMapping(value = "/admin/clients/new", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String createForm(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String template) {
    ClientCreateTemplate preset = ClientCreateTemplate.fromQuery(template);
    return page(
        request,
        "New client",
        "<h1>New client</h1>" + clientForm(null, request, preset, "name"));
  }

  @GetMapping(value = "/admin/clients/{id}", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String detail(HttpServletRequest request, @PathVariable int id) {
    ClientConfiguration c =
        repos().clients().findByPk(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    StringBuilder body = new StringBuilder("<h1>Client ").append(AdminHtml.esc(c.clientId())).append("</h1>");
    String initialTab = "name";
    String tabParam = request.getParameter("tab");
    if (tabParam != null && !tabParam.isBlank()) {
      initialTab = tabParam.trim();
    } else if (AdminRequests.flashAttr(request, "secretReveal") != null) {
      initialTab = "secrets";
    }
    body.append(clientForm(c, request, null, initialTab));
    return page(request, c.clientId(), body.toString());
  }

  @PostMapping("/admin/clients")
  public RedirectView create(HttpServletRequest request, RedirectAttributes redirect) {
    try {
      int id = repos().clients().insert(readWrite(request));
      syncExternalLoginProperties(id, request);
      AdminRequests.notice(redirect, "Created client " + id);
      return new RedirectView("/admin/clients/" + id, true);
    } catch (UncheckedSqlException | IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
      return new RedirectView("/admin/clients/new", true);
    }
  }

  @PostMapping("/admin/clients/{id}")
  public RedirectView update(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    repos().clients().update(id, readWrite(request));
    syncExternalLoginProperties(id, request);
    AdminRequests.notice(redirect, "Saved");
    return new RedirectView("/admin/clients/" + id, true);
  }

  private void syncExternalLoginProperties(int clientPk, HttpServletRequest request) {
    boolean google = AdminRequests.checked(request, "externalGoogle");
    ExternalLoginLinkMode googleMode =
        ExternalLoginLinkMode.fromConfig(request.getParameter("externalGoogleLinkMode"));
    boolean microsoft = AdminRequests.checked(request, "externalMicrosoft");
    ExternalLoginLinkMode microsoftMode =
        ExternalLoginLinkMode.fromConfig(request.getParameter("externalMicrosoftLinkMode"));
    boolean whatsapp = AdminRequests.checked(request, "externalWhatsapp");
    ExternalLoginLinkMode whatsappMode =
        ExternalLoginLinkMode.fromConfig(request.getParameter("externalWhatsappLinkMode"));
    boolean wechat = AdminRequests.checked(request, "externalWechat");
    ExternalLoginLinkMode wechatMode =
        ExternalLoginLinkMode.fromConfig(request.getParameter("externalWechatLinkMode"));
    repos()
        .clients()
        .setProperty(
            clientPk, ExternalLoginClientSettings.GOOGLE_ENABLED_KEY, google ? "true" : "false");
    repos()
        .clients()
        .setProperty(
            clientPk, ExternalLoginClientSettings.GOOGLE_LINK_MODE_KEY, googleMode.configValue());
    repos()
        .clients()
        .setProperty(
            clientPk,
            ExternalLoginClientSettings.MICROSOFT_ENABLED_KEY,
            microsoft ? "true" : "false");
    repos()
        .clients()
        .setProperty(
            clientPk,
            ExternalLoginClientSettings.MICROSOFT_LINK_MODE_KEY,
            microsoftMode.configValue());
    repos()
        .clients()
        .setProperty(
            clientPk,
            ExternalLoginClientSettings.WHATSAPP_ENABLED_KEY,
            whatsapp ? "true" : "false");
    repos()
        .clients()
        .setProperty(
            clientPk,
            ExternalLoginClientSettings.WHATSAPP_LINK_MODE_KEY,
            whatsappMode.configValue());
    repos()
        .clients()
        .setProperty(
            clientPk, ExternalLoginClientSettings.WECHAT_ENABLED_KEY, wechat ? "true" : "false");
    repos()
        .clients()
        .setProperty(
            clientPk, ExternalLoginClientSettings.WECHAT_LINK_MODE_KEY, wechatMode.configValue());
  }

  @PostMapping("/admin/clients/{id}/delete")
  public RedirectView delete(@PathVariable int id, RedirectAttributes redirect) {
    repos().clients().delete(id);
    AdminRequests.notice(redirect, "Deleted client " + id);
    return new RedirectView("/admin/clients", true);
  }

  @PostMapping("/admin/clients/{id}/secrets")
  public RedirectView addSecret(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    String value = request.getParameter("value");
    if (value == null || value.isBlank()) {
      AdminRequests.notice(redirect, "Secret value is required");
      return new RedirectView("/admin/clients/" + id + "?tab=secrets", true);
    }
    String type = request.getParameter("type");
    String hashType = request.getParameter("hashType");
    String description = request.getParameter("description");
    Instant expiration = parseExpiration(request.getParameter("expiration"));
    String hashed = SharedSecretHasher.hashForType(value.trim(), hashType);
    repos().clients().addSecret(id, type, hashed, description, expiration);
    AdminRequests.notice(
        redirect,
        "Secret added ("
            + (hashType == null || hashType.isBlank() ? "Sha256" : hashType.trim())
            + "). Copy the plaintext below ??it will not be shown again.");
    AdminRequests.secretReveal(redirect, value.trim());
    return new RedirectView("/admin/clients/" + id + "?tab=secrets", true);
  }

  @PostMapping("/admin/clients/{id}/secrets/{secretId}/delete")
  public RedirectView deleteSecret(
      @PathVariable int id, @PathVariable int secretId, RedirectAttributes redirect) {
    repos().clients().deleteSecret(id, secretId);
    AdminRequests.notice(redirect, "Secret deleted");
    return new RedirectView("/admin/clients/" + id + "?tab=secrets", true);
  }

  @PostMapping("/admin/clients/{id}/claims")
  public RedirectView addClaim(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    String type = request.getParameter("type");
    String value = request.getParameter("value");
    try {
      repos().clients().addClaim(id, type, value);
      AdminRequests.notice(redirect, "Claim added");
    } catch (IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
    }
    return new RedirectView("/admin/clients/" + id + "?tab=claims", true);
  }

  @PostMapping("/admin/clients/{id}/claims/{claimId}/delete")
  public RedirectView deleteClaim(
      @PathVariable int id, @PathVariable int claimId, RedirectAttributes redirect) {
    repos().clients().deleteClaim(id, claimId);
    AdminRequests.notice(redirect, "Claim deleted");
    return new RedirectView("/admin/clients/" + id + "?tab=claims", true);
  }

  @PostMapping("/admin/clients/{id}/properties")
  public RedirectView addProperty(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    String key = request.getParameter("key");
    String value = request.getParameter("value");
    try {
      repos().clients().addProperty(id, key, value);
      AdminRequests.notice(redirect, "Property added");
    } catch (IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
    }
    return new RedirectView("/admin/clients/" + id + "?tab=properties", true);
  }

  @PostMapping("/admin/clients/{id}/properties/{propertyId}/delete")
  public RedirectView deleteProperty(
      @PathVariable int id, @PathVariable int propertyId, RedirectAttributes redirect) {
    repos().clients().deleteProperty(id, propertyId);
    AdminRequests.notice(redirect, "Property deleted");
    return new RedirectView("/admin/clients/" + id + "?tab=properties", true);
  }

  private static final String[] GRANT_PRESETS = {
    "authorization_code",
    "client_credentials",
    "refresh_token",
    "implicit",
    "password",
    "urn:ietf:params:oauth:grant-type:device_code",
    "delegation"
  };

  private static final String[] SCOPE_PRESETS = {
    "openid", "profile", "email", "phone", "address", "roles", "offline_access"
  };

  private String clientForm(
      ClientConfiguration existing,
      HttpServletRequest request,
      ClientCreateTemplate template,
      String initialTab) {
    boolean create = existing == null;
    ClientCreateTemplate preset =
        create ? (template == null ? ClientCreateTemplate.EMPTY : template) : null;
    String action = create ? "/admin/clients" : "/admin/clients/" + existing.id();
    String startTab =
        initialTab == null || initialTab.isBlank()
            ? "name"
            : initialTab.trim().toLowerCase(java.util.Locale.ROOT);
    if (create && isSideOnlyTab(startTab)) {
      startTab = "name";
    }
    String csrf =
        AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));

    StringBuilder html = new StringBuilder("<div class=\"editor\">");
    if (create) {
      html.append(
          "<p class=\"lead\">Configure an OAuth / OIDC client. After create, add Secrets, Claims, and Properties on dedicated tabs.</p>");
      html.append(AdminHtml.clientTemplatePicker(preset));
    }

    boolean requireSecret =
        create ? preset.requireClientSecret() : existing.requireClientSecret();
    boolean requirePkce = create ? preset.requirePkce() : existing.requirePkce();
    boolean allowOffline =
        create ? preset.allowOfflineAccess() : existing.allowOfflineAccess();
    String grantText =
        create ? preset.grantTypes() : AdminHtml.joinLines(existing.grantTypes());
    String scopeText = create ? preset.scopes() : AdminHtml.joinLines(existing.scopes());

    // Settings panels live in the client form; Secrets / Claims / Properties are sibling panels.
    html.append("<div class=\"tabs\" data-tabs data-initial-tab=\"")
        .append(AdminHtml.esc(startTab))
        .append("\">");
    html.append("<div class=\"tab-list\" role=\"tablist\" aria-label=\"Client settings\">");
    html.append(tabButton("name", "Name", "name".equals(startTab)));
    html.append(tabButton("basic", "Basic", "basic".equals(startTab)));
    html.append(tabButton("auth", "Authentication", "auth".equals(startTab)));
    html.append(tabButton("token", "Token", "token".equals(startTab)));
    html.append(tabButton("consent", "Consent", "consent".equals(startTab)));
    html.append(tabButton("device", "Device", "device".equals(startTab)));
    if (!create) {
      html.append(tabButton("claims", "Claims", "claims".equals(startTab)));
      html.append(tabButton("properties", "Properties", "properties".equals(startTab)));
      html.append(tabButton("secrets", "Secrets", "secrets".equals(startTab)));
    }
    html.append("</div>");

    html.append("<form method=\"post\" action=\"")
        .append(action)
        .append("\" data-client-settings>");
    html.append(csrf);

    // Name
    html.append("<div class=\"tab-panel")
        .append("name".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"name\" role=\"tabpanel\">");
    html.append("<h2>Name</h2>");
    html.append("<div class=\"grid-2\">");
    html.append("<div class=\"field\"><label>Client ID</label><input name=\"clientId\" required autocomplete=\"off\" placeholder=\"my-app\" value=\"")
        .append(AdminHtml.esc(create ? "" : existing.clientId()))
        .append("\"><span class=\"hint\">Stable public identifier (Clients.ClientId).</span></div>");
    html.append("<div class=\"field\"><label>Display name</label><input name=\"clientName\" placeholder=\"My application\" value=\"")
        .append(AdminHtml.esc(create ? "" : existing.clientName()))
        .append("\"><span class=\"hint\">Shown on consent / admin lists.</span></div>");
    html.append("</div><div class=\"checks\" style=\"margin-top:.75rem\">");
    html.append(
        AdminHtml.flagCheckbox(
            "enabled",
            "Enabled",
            "Disabled clients cannot obtain tokens.",
            create || existing.enabled()));
    html.append("</div></div>");

    // Basic
    html.append("<div class=\"tab-panel")
        .append("basic".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"basic\" role=\"tabpanel\">");
    html.append("<h2>Basic</h2>");
    html.append("<div class=\"checks\">");
    html.append(
        AdminHtml.flagCheckbox(
            "requireClientSecret",
            "Require client secret",
            "Confidential clients; turn off for public SPA / native + PKCE.",
            requireSecret));
    html.append(
        AdminHtml.flagCheckbox(
            "requirePkce",
            "Require PKCE",
            "Recommended for authorization_code.",
            requirePkce));
    html.append(
        AdminHtml.flagCheckbox(
            "allowOfflineAccess",
            "Allow offline access",
            "Permit refresh tokens when scope offline_access is requested.",
            allowOffline));
    html.append("</div>");
    html.append(
        AdminHtml.listPicker(
            "grantTypes",
            "Allowed grant types",
            "Toggle common grants; edit the list for custom values (one per line).",
            grantText,
            GRANT_PRESETS));
    html.append(
        AdminHtml.listPicker(
            "scopes",
            "Allowed scopes",
            "Must exist as Identity / API scopes in this store.",
            scopeText,
            SCOPE_PRESETS));
    if (!create) {
      html.append(
          "<p class=\"hint\" style=\"margin:.85rem 0 0\">Client claims and properties are managed on the "
              + "<strong>Claims</strong> and <strong>Properties</strong> tabs (not overwritten by Save).</p>");
    }
    html.append("</div>");

    // Authentication / logout
    html.append("<div class=\"tab-panel")
        .append("auth".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"auth\" role=\"tabpanel\">");
    html.append("<h2>Authentication / logout</h2>");
    html.append("<div class=\"field\"><label>Redirect URIs</label>");
    html.append(
        "<span class=\"hint\">One absolute URI per line (authorization code callbacks).</span>");
    html.append("<textarea name=\"redirectUris\" rows=\"4\" spellcheck=\"false\" placeholder=\"https://app.example/signin-oidc\">")
        .append(AdminHtml.esc(create ? "" : AdminHtml.joinLines(existing.redirectUris())))
        .append("</textarea></div>");
    html.append("<div class=\"field\" style=\"margin-top:.75rem\"><label>Post-logout redirect URIs</label>");
    html.append("<span class=\"hint\">Allowed return URLs after /connect/endsession.</span>");
    html.append("<textarea name=\"postLogoutRedirectUris\" rows=\"3\" spellcheck=\"false\" placeholder=\"https://app.example/\">")
        .append(AdminHtml.esc(create ? "" : AdminHtml.joinLines(existing.postLogoutRedirectUris())))
        .append("</textarea></div>");
    html.append("<div class=\"grid-2\" style=\"margin-top:.75rem\">");
    html.append("<div class=\"field\"><label>Front-channel logout URI</label><input type=\"url\" name=\"frontChannelLogoutUri\" placeholder=\"https://app.example/logout\" value=\"")
        .append(AdminHtml.esc(create ? "" : existing.frontChannelLogoutUri()))
        .append("\"><span class=\"hint\">Browser logout notification.</span></div>");
    html.append("<div class=\"field\"><label>Back-channel logout URI</label><input type=\"url\" name=\"backChannelLogoutUri\" placeholder=\"https://app.example/backchannel-logout\" value=\"")
        .append(AdminHtml.esc(create ? "" : existing.backChannelLogoutUri()))
        .append("\"><span class=\"hint\">Server-to-server logout token.</span></div>");
    html.append("</div>");
    html.append("<div class=\"field\" style=\"margin-top:.75rem\"><label>CORS origins</label>");
    html.append("<span class=\"hint\">Browser origins allowed to call the token endpoint.</span>");
    html.append("<textarea name=\"corsOrigins\" rows=\"3\" spellcheck=\"false\" placeholder=\"https://app.example\">")
        .append(AdminHtml.esc(create ? "" : AdminHtml.joinLines(existing.corsOrigins())))
        .append("</textarea></div>");
    ExternalLoginClientSettings ext =
        create ? ExternalLoginClientSettings.disabled() : ExternalLoginClientSettings.from(existing);
    html.append("<h2 style=\"margin-top:1.35rem\">External login (STS)</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">When this client starts <code>/connect/authorize</code>, "
            + "STS can offer Google / Microsoft / WhatsApp / WeChat QR if global credentials are configured. Link mode applies after the IdP returns. "
            + "Stored in <code>ClientProperties</code> keys <code>skoruba4j.external.google*</code> / "
            + "<code>microsoft*</code> / <code>whatsapp*</code> / <code>wechat*</code>.</p>");
    html.append("<div class=\"checks\">");
    html.append(
        AdminHtml.flagCheckbox(
            "externalGoogle",
            "Allow Google login",
            "Show Sign in with Google on STS for this client.",
            ext.googleEnabled()));
    html.append("</div>");
    html.append("<div class=\"field\" style=\"margin-top:.65rem;max-width:22rem\">");
    html.append("<label for=\"externalGoogleLinkMode\">Google account link mode</label>");
    html.append("<select id=\"externalGoogleLinkMode\" name=\"externalGoogleLinkMode\">");
    appendLinkModeOptions(html, ext.googleLinkMode());
    html.append("</select></div>");
    html.append("<div class=\"checks\" style=\"margin-top:1rem\">");
    html.append(
        AdminHtml.flagCheckbox(
            "externalMicrosoft",
            "Allow Microsoft login",
            "Show Sign in with Microsoft on STS for this client.",
            ext.microsoftEnabled()));
    html.append("</div>");
    html.append("<div class=\"field\" style=\"margin-top:.65rem;max-width:22rem\">");
    html.append("<label for=\"externalMicrosoftLinkMode\">Microsoft account link mode</label>");
    html.append("<select id=\"externalMicrosoftLinkMode\" name=\"externalMicrosoftLinkMode\">");
    appendLinkModeOptions(html, ext.microsoftLinkMode());
    html.append("</select></div>");
    html.append("<div class=\"checks\" style=\"margin-top:1rem\">");
    html.append(
        AdminHtml.flagCheckbox(
            "externalWhatsapp",
            "Allow WhatsApp QR login",
            "Show Sign in with WhatsApp on STS (scan QR, reply LOGIN). Requires STS WhatsApp Cloud API config.",
            ext.whatsappEnabled()));
    html.append("</div>");
    html.append("<div class=\"field\" style=\"margin-top:.65rem;max-width:22rem\">");
    html.append("<label for=\"externalWhatsappLinkMode\">WhatsApp account link mode</label>");
    html.append("<select id=\"externalWhatsappLinkMode\" name=\"externalWhatsappLinkMode\">");
    appendLinkModeOptions(html, ext.whatsappLinkMode());
    html.append("</select></div>");
    html.append("<div class=\"checks\" style=\"margin-top:1rem\">");
    html.append(
        AdminHtml.flagCheckbox(
            "externalWechat",
            "Allow WeChat QR login",
            "Show Sign in with WeChat on STS (Open Platform website app). Requires STS WeChat app-id/secret.",
            ext.wechatEnabled()));
    html.append("</div>");
    html.append("<div class=\"field\" style=\"margin-top:.65rem;max-width:22rem\">");
    html.append("<label for=\"externalWechatLinkMode\">WeChat account link mode</label>");
    html.append("<select id=\"externalWechatLinkMode\" name=\"externalWechatLinkMode\">");
    appendLinkModeOptions(html, ext.wechatLinkMode());
    html.append("</select></div>");
    html.append("</div>");

    // Token
    html.append("<div class=\"tab-panel")
        .append("token".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"token\" role=\"tabpanel\">");
    html.append("<h2>Token</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">Lifetimes are stored as seconds; edit with days / hours / minutes.</p>");
    html.append("<div class=\"checks\" style=\"margin-bottom:.75rem\">");
    html.append(
        AdminHtml.flagCheckbox(
            "alwaysIncludeUserClaimsInIdToken",
            "Always include user claims in id_token",
            "Larger id_token; usually leave off and use UserInfo.",
            !create && existing.alwaysIncludeUserClaimsInIdToken()));
    html.append("</div>");
    html.append(AdminHtml.accessTokenTypeSelect(create ? 0 : existing.accessTokenType()));
    html.append("<div class=\"grid-2\" style=\"margin-top:.85rem\">");
    html.append(
        AdminHtml.durationField(
            "identityTokenLifetime",
            "Identity token (id_token)",
            create ? 300 : existing.identityTokenLifetime()));
    html.append(
        AdminHtml.durationField(
            "accessTokenLifetime",
            "Access token",
            create ? 3600 : existing.accessTokenLifetime()));
    html.append(
        AdminHtml.durationField(
            "authorizationCodeLifetime",
            "Authorization code",
            create ? 300 : existing.authorizationCodeLifetime()));
    html.append(
        AdminHtml.durationField(
            "absoluteRefreshTokenLifetime",
            "Absolute refresh token",
            create ? 2_592_000 : existing.absoluteRefreshTokenLifetime()));
    html.append(
        AdminHtml.durationField(
            "slidingRefreshTokenLifetime",
            "Sliding refresh token",
            create ? 1_296_000 : existing.slidingRefreshTokenLifetime()));
    html.append("</div></div>");

    // Consent
    html.append("<div class=\"tab-panel")
        .append("consent".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"consent\" role=\"tabpanel\">");
    html.append("<h2>Consent screen</h2>");
    html.append("<div class=\"checks\">");
    html.append(
        AdminHtml.flagCheckbox(
            "requireConsent",
            "Require consent",
            "Prompt the user to approve scopes.",
            !create && existing.requireConsent()));
    html.append("</div>");
    html.append(
        "<p class=\"hint\" style=\"margin:.85rem 0 0\">Skoruba also edits Client URI, Logo URI, and Allow remember consent here. "
            + "Those columns are not wired in this Admin form yet ??use Manage / SQL only if you need them urgently.</p>");
    html.append("</div>");

    // Device
    html.append("<div class=\"tab-panel")
        .append("device".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"device\" role=\"tabpanel\">");
    html.append("<h2>Device flow</h2>");
    html.append("<div class=\"checks\">");
    html.append(
        "<label><input type=\"checkbox\" data-device-grant>"
            + "<span>Enable device authorization grant"
            + "<span class=\"sub\">Adds <code>urn:ietf:params:oauth:grant-type:device_code</code> to Basic ??grant types.</span>"
            + "</span></label>");
    html.append("</div>");
    html.append(
        "<p class=\"hint\" style=\"margin:.85rem 0 0\">Device code lifetime and related IS4 knobs are not editable here yet. "
            + "STS device-flow support may still be partial ??verify before production use.</p>");
    html.append("</div>");

    html.append("<div class=\"form-actions\" data-settings-actions");
    if (isSideOnlyTab(startTab)) {
      html.append(" hidden");
    }
    html.append(">");
    html.append("<button type=\"submit\">").append(create ? "Create client" : "Save changes").append("</button>");
    html.append("<a href=\"/admin/clients\">Cancel</a>");
    html.append("</div>");
    html.append("</form>");

    if (!create) {
      html.append("<div class=\"tab-panel")
          .append("claims".equals(startTab) ? " is-active" : "")
          .append("\" data-panel=\"claims\" role=\"tabpanel\">");
      html.append(claimsPanel(existing, existing.id(), csrf));
      html.append("</div>");
      html.append("<div class=\"tab-panel")
          .append("properties".equals(startTab) ? " is-active" : "")
          .append("\" data-panel=\"properties\" role=\"tabpanel\">");
      html.append(propertiesPanel(existing, existing.id(), csrf));
      html.append("</div>");
      html.append("<div class=\"tab-panel")
          .append("secrets".equals(startTab) ? " is-active" : "")
          .append("\" data-panel=\"secrets\" role=\"tabpanel\">");
      html.append(secretsPanel(existing, existing.id(), csrf, request));
      html.append("</div>");
    }

    html.append("</div>"); // tabs
    html.append("</div>"); // editor
    html.append(AdminHtml.clientEditorScript());
    return html.toString();
  }

  private static boolean isSideOnlyTab(String tab) {
    return "secrets".equals(tab) || "claims".equals(tab) || "properties".equals(tab);
  }

  private static String tabButton(String id, String label, boolean selected) {
    return "<button type=\"button\" role=\"tab\" data-tab=\""
        + id
        + "\" aria-selected=\""
        + (selected ? "true" : "false")
        + "\">"
        + AdminHtml.esc(label)
        + "</button>";
  }

  private static String claimsPanel(ClientConfiguration client, int id, String csrf) {
    StringBuilder html = new StringBuilder();
    html.append("<h2>Client claims</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">IS4 <code>ClientClaims</code>: fixed type/value pairs emitted for this client "
            + "(when Always Send Client Claims / claim mapping applies).</p>");
    html.append("<form method=\"post\" action=\"/admin/clients/")
        .append(id)
        .append("/claims\">")
        .append(csrf);
    html.append("<div class=\"grid-2\">");
    html.append("<div class=\"field\"><label>Type</label>");
    html.append(
        "<input name=\"type\" type=\"text\" required autocomplete=\"off\" spellcheck=\"false\" placeholder=\"client_type\">");
    html.append("</div>");
    html.append("<div class=\"field\"><label>Value</label>");
    html.append(
        "<input name=\"value\" type=\"text\" autocomplete=\"off\" spellcheck=\"false\" placeholder=\"internal\">");
    html.append("</div></div>");
    html.append("<p style=\"margin:.9rem 0 0\"><button type=\"submit\">Add claim</button></p>");
    html.append("</form>");
    html.append("<h2 style=\"margin-top:1.35rem\">Existing claims</h2>");
    if (client.claims() == null || client.claims().isEmpty()) {
      html.append("<p class=\"hint\">No claims yet.</p>");
    } else {
      html.append("<table><tr><th>Type</th><th>Value</th><th></th></tr>");
      for (var claim : client.claims()) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(claim.type()))
            .append("</td><td>")
            .append(AdminHtml.esc(claim.value() == null ? "" : claim.value()))
            .append("</td><td>");
        html.append("<form method=\"post\" action=\"/admin/clients/")
            .append(id)
            .append("/claims/")
            .append(claim.id())
            .append("/delete\"")
            .append(AdminHtml.dataConfirm("Delete this claim?"))
            .append(">")
            .append(csrf)
            .append("<button type=\"submit\">Delete</button></form></td></tr>");
      }
      html.append("</table>");
    }
    return html.toString();
  }

  private static String propertiesPanel(ClientConfiguration client, int id, String csrf) {
    StringBuilder html = new StringBuilder();
    html.append("<h2>Client properties</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">IS4 <code>ClientProperties</code> bag (key/value). "
            + "Used by custom code and some Skoruba extensions ??not OAuth protocol fields.</p>");
    html.append("<form method=\"post\" action=\"/admin/clients/")
        .append(id)
        .append("/properties\">")
        .append(csrf);
    html.append("<div class=\"grid-2\">");
    html.append("<div class=\"field\"><label>Key</label>");
    html.append(
        "<input name=\"key\" type=\"text\" required autocomplete=\"off\" spellcheck=\"false\" placeholder=\"skoruba\">");
    html.append("</div>");
    html.append("<div class=\"field\"><label>Value</label>");
    html.append(
        "<input name=\"value\" type=\"text\" autocomplete=\"off\" spellcheck=\"false\" placeholder=\"1\">");
    html.append("</div></div>");
    html.append("<p style=\"margin:.9rem 0 0\"><button type=\"submit\">Add property</button></p>");
    html.append("</form>");
    html.append("<h2 style=\"margin-top:1.35rem\">Existing properties</h2>");
    if (client.properties() == null || client.properties().isEmpty()) {
      html.append("<p class=\"hint\">No properties yet.</p>");
    } else {
      html.append("<table><tr><th>Key</th><th>Value</th><th></th></tr>");
      for (var property : client.properties()) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(property.key()))
            .append("</td><td>")
            .append(AdminHtml.esc(property.value() == null ? "" : property.value()))
            .append("</td><td>");
        html.append("<form method=\"post\" action=\"/admin/clients/")
            .append(id)
            .append("/properties/")
            .append(property.id())
            .append("/delete\"")
            .append(AdminHtml.dataConfirm("Delete this property?"))
            .append(">")
            .append(csrf)
            .append("<button type=\"submit\">Delete</button></form></td></tr>");
      }
      html.append("</table>");
    }
    return html.toString();
  }

  private static String secretsPanel(
      ClientConfiguration client, int id, String csrf, HttpServletRequest request) {
    StringBuilder html = new StringBuilder();
    html.append("<h2>Client secrets</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">IS4 SharedSecret: plaintext is hashed before save. "
            + "Stored hashes are never shown again as recoverable secrets.</p>");
    String reveal = AdminRequests.flashAttr(request, "secretReveal");
    if (reveal != null && !reveal.isBlank()) {
      html.append("<div class=\"callout danger\"><strong>Copy this secret now.</strong><br>");
      html.append("You will not be able to retrieve it after leaving this page.<br>");
      html.append("<code>").append(AdminHtml.esc(reveal)).append("</code></div>");
    }
    html.append("<form method=\"post\" action=\"/admin/clients/")
        .append(id)
        .append("/secrets\" data-secret-form>")
        .append(csrf);
    html.append("<div class=\"grid-2\">");
    html.append("<div class=\"field\"><label>Secret type</label>");
    html.append("<select name=\"type\">");
    html.append("<option value=\"SharedSecret\" selected>Shared Secret</option>");
    html.append("<option value=\"X509Thumbprint\">X509 Thumbprint</option>");
    html.append("</select></div>");
    html.append("<div class=\"field\"><label>Hash type</label>");
    html.append("<select name=\"hashType\">");
    html.append("<option value=\"Sha256\" selected>Sha256</option>");
    html.append("<option value=\"Sha512\">Sha512</option>");
    html.append("</select>");
    html.append(
        "<span class=\"hint\">Hash type applies to Shared Secret only. Prefer Sha256 (IS4 default).</span>");
    html.append("</div></div>");
    html.append("<div class=\"field\" style=\"margin-top:.65rem\"><label>Secret value</label>");
    html.append("<div class=\"secret-row\">");
    html.append(
        "<input name=\"value\" id=\"secret-value\" type=\"text\" autocomplete=\"off\" spellcheck=\"false\" required placeholder=\"Paste or generate a secret\">");
    html.append("<button type=\"button\" data-generate-secret title=\"Generate random secret\">Generate</button>");
    html.append("</div>");
    html.append(
        "<div class=\"callout warn\">Copy the new client secret value. You won't be able to retrieve it after you save.</div>");
    html.append("</div>");
    html.append("<div class=\"grid-2\">");
    html.append("<div class=\"field\"><label>Expiration</label>");
    html.append("<input type=\"date\" name=\"expiration\">");
    html.append("<span class=\"hint\">Optional. Leave blank for no expiry.</span></div>");
    html.append("<div class=\"field\"><label>Description</label>");
    html.append("<input name=\"description\" type=\"text\" maxlength=\"200\" placeholder=\"e.g. rotated 2026-10\">");
    html.append("</div></div>");
    html.append("<p style=\"margin:.9rem 0 0\"><button type=\"submit\">Add client secret</button></p>");
    html.append("</form>");

    html.append("<h2 style=\"margin-top:1.35rem\">Existing secrets</h2>");
    if (client.secrets() == null || client.secrets().isEmpty()) {
      html.append("<p class=\"hint\">No secrets yet.</p>");
    } else {
      html.append(
          "<table><tr><th>Type</th><th>Description</th><th>Expiration</th><th>Created</th><th></th></tr>");
      for (var secret : client.secrets()) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(secret.type()))
            .append("</td><td>")
            .append(AdminHtml.esc(secret.description() == null ? "" : secret.description()))
            .append("</td><td>")
            .append(AdminHtml.esc(formatInstant(secret.expiration())))
            .append("</td><td>")
            .append(AdminHtml.esc(formatInstant(secret.created())))
            .append("</td><td>");
        html.append("<form method=\"post\" action=\"/admin/clients/")
            .append(id)
            .append("/secrets/")
            .append(secret.id())
            .append("/delete\"")
            .append(AdminHtml.dataConfirm("Delete this secret?"))
            .append(">")
            .append(csrf)
            .append("<button type=\"submit\">Delete</button></form></td></tr>");
      }
      html.append("</table>");
    }
    html.append("<div style=\"margin-top:1.5rem;padding-top:1rem;border-top:1px solid var(--line)\">");
    html.append("<h2>Danger zone</h2>");
    html.append("<form method=\"post\" action=\"/admin/clients/")
        .append(id)
        .append("/delete\"")
        .append(
            AdminHtml.dataConfirm(
                "Delete this client? This cannot be undone.", "Delete client", "Delete client", true))
        .append(">")
        .append(csrf)
        .append("<button type=\"submit\">Delete client</button></form>");
    html.append("</div>");
    html.append(secretFormScript());
    return html.toString();
  }

  private static Instant parseExpiration(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim()).atStartOfDay(ZoneOffset.UTC).toInstant();
    } catch (DateTimeParseException e) {
      try {
        return Instant.parse(raw.trim());
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
  }

  private static final DateTimeFormatter DISPLAY_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private static String formatInstant(Instant value) {
    if (value == null) {
      return "—";
    }
    return DISPLAY_UTC.format(value) + " UTC";
  }

  private static String secretFormScript() {
    return """
        <script>
        (function(){
          function generateSecret(){
            var b=new Uint8Array(16);
            crypto.getRandomValues(b);
            b[6]=b[6]&0x0f|0x40;
            b[8]=b[8]&0x3f|0x80;
            var h=[];
            for(var i=0;i<16;i++) h.push(('0'+b[i].toString(16)).slice(-2));
            return h.slice(0,4).join('')+'-'+h.slice(4,6).join('')+'-'+h.slice(6,8).join('')+'-'+h.slice(8,10).join('')+'-'+h.slice(10).join('');
          }
          document.querySelectorAll('[data-generate-secret]').forEach(function(btn){
            btn.addEventListener('click',function(){
              var form=btn.closest('form');
              var input=form&&form.querySelector('#secret-value,input[name=value]');
              if(!input) return;
              input.type='text';
              input.value=generateSecret();
              input.focus();
              input.select();
            });
          });
        })();
        </script>
        """;
  }

  private static void appendLinkModeOptions(StringBuilder html, ExternalLoginLinkMode selected) {
    for (ExternalLoginLinkMode mode : ExternalLoginLinkMode.values()) {
      boolean on = mode == selected;
      String label =
          switch (mode) {
            case LINK_EXISTING -> "Link existing only (same email; else error)";
            case AUTO_CREATE -> "Auto-create user if email unknown";
            case CONFIRM -> "Confirm page then create user";
          };
      html.append("<option value=\"")
          .append(AdminHtml.esc(mode.configValue()))
          .append('"')
          .append(on ? " selected" : "")
          .append('>')
          .append(AdminHtml.esc(label))
          .append("</option>");
    }
  }

  private ClientWrite readWrite(HttpServletRequest request) {
    String clientId = request.getParameter("clientId");
    if (clientId == null || clientId.isBlank()) {
      throw new IllegalArgumentException("clientId is required");
    }
    return new ClientWrite(
        clientId.trim(),
        Optional.ofNullable(request.getParameter("clientName")).orElse(clientId),
        AdminRequests.checked(request, "enabled"),
        AdminRequests.checked(request, "requireClientSecret"),
        AdminRequests.checked(request, "requirePkce"),
        AdminRequests.checked(request, "allowOfflineAccess"),
        AdminRequests.checked(request, "requireConsent"),
        AdminRequests.checked(request, "alwaysIncludeUserClaimsInIdToken"),
        AdminRequests.intParam(request, "identityTokenLifetime", 300),
        AdminRequests.intParam(request, "accessTokenLifetime", 3600),
        AdminRequests.intParam(request, "authorizationCodeLifetime", 300),
        AdminRequests.intParam(request, "absoluteRefreshTokenLifetime", 2_592_000),
        AdminRequests.intParam(request, "slidingRefreshTokenLifetime", 1_296_000),
        AdminRequests.intParam(request, "accessTokenType", 0),
        Optional.ofNullable(request.getParameter("frontChannelLogoutUri")).orElse(""),
        Optional.ofNullable(request.getParameter("backChannelLogoutUri")).orElse(""),
        AdminHtml.splitLines(request.getParameter("grantTypes")),
        AdminHtml.splitLines(request.getParameter("scopes")),
        AdminHtml.splitLines(request.getParameter("redirectUris")),
        AdminHtml.splitLines(request.getParameter("postLogoutRedirectUris")),
        AdminHtml.splitLines(request.getParameter("corsOrigins")),
        List.of(),
        List.of());
  }

  private JdbcRepositories repos() {
    return jdbc.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "database not configured"));
  }

  private static String page(HttpServletRequest request, String title, String body) {
    return AdminHtml.page(
        title,
        AdminRequests.flash(request),
        AdminRequests.csrfName(request),
        AdminRequests.csrfToken(request),
        body);
  }
}
