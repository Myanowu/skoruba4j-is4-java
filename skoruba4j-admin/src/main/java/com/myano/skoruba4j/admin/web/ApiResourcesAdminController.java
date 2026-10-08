package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.ApiResourceAdminRepository;
import com.myano.skoruba4j.domain.configstore.ApiResourceConfiguration;
import com.myano.skoruba4j.domain.configstore.ApiResourceSummary;
import com.myano.skoruba4j.domain.configstore.ApiResourceWrite;
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
public class ApiResourcesAdminController {
  private static final String[] ALGORITHM_PRESETS = {
    "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"
  };

  private static final String[] CLAIM_PRESETS = {
    "role", "name", "email", "sub", "preferred_username", "phone_number", "address"
  };

  private final Optional<JdbcRepositories> jdbc;

  public ApiResourcesAdminController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/admin/api-resources", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String searchText,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    PageQuery query = PageQuery.of(searchText, page, 20);
    StringBuilder body =
        new StringBuilder("<h1>")
            .append(AdminHtml.esc(Messages.t("apiResources.title")))
            .append("</h1>");
    body.append(
        "<p class=\"lead\">API audiences (<code>aud</code> = <code>ApiResources.Name</code>). "
            + "Edit scopes, claims, secrets, and signing algorithms on each resource.</p>");
    body.append("<p><a href=\"/admin/api-resources/new\">New API resource</a></p>");
    body.append(AdminHtml.searchBar("/admin/api-resources", query.searchText(), query.page()));
    PageResult<ApiResourceSummary> result = repos().apiResourceAdmin().search(query);
    body.append("<table><tr><th>Id</th><th>Name</th><th>DisplayName</th><th>Enabled</th></tr>");
    for (ApiResourceSummary item : result.items()) {
      body.append("<tr><td>")
          .append(item.id())
          .append("</td><td><a href=\"/admin/api-resources/")
          .append(item.id())
          .append("\">")
          .append(AdminHtml.esc(item.name()))
          .append("</a></td><td>")
          .append(AdminHtml.esc(item.displayName()))
          .append("</td><td>")
          .append(item.enabled())
          .append("</td></tr>");
    }
    body.append("</table>");
    body.append(
        AdminHtml.pager(
            "/admin/api-resources",
            query.searchText(),
            result.page(),
            result.pageSize(),
            result.totalCount()));
    return page(request, Messages.t("apiResources.title"), body.toString());
  }

  @GetMapping(value = "/admin/api-resources/new", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String createForm(HttpServletRequest request) {
    return page(
        request,
        "New API resource",
        "<h1>New API resource</h1>" + resourceForm(null, request, "basic"));
  }

  @GetMapping(value = "/admin/api-resources/{id}", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String detail(HttpServletRequest request, @PathVariable int id) {
    ApiResourceConfiguration resource =
        repos()
            .apiResourceAdmin()
            .findFull(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String initialTab = "basic";
    String tabParam = request.getParameter("tab");
    if (tabParam != null && !tabParam.isBlank()) {
      initialTab = tabParam.trim();
    } else if (AdminRequests.flashAttr(request, "secretReveal") != null) {
      initialTab = "secrets";
    }
    StringBuilder body =
        new StringBuilder("<h1>Api resource ")
            .append(AdminHtml.esc(resource.name()))
            .append("</h1>");
    body.append(resourceForm(resource, request, initialTab));
    return page(request, resource.name(), body.toString());
  }

  @PostMapping("/admin/api-resources")
  public RedirectView create(HttpServletRequest request, RedirectAttributes redirect) {
    try {
      int id = repos().apiResourceAdmin().insert(readWrite(request));
      AdminRequests.notice(redirect, "Created API resource " + id);
      return new RedirectView("/admin/api-resources/" + id, true);
    } catch (UncheckedSqlException | IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
      return new RedirectView("/admin/api-resources/new", true);
    }
  }

  @PostMapping("/admin/api-resources/{id}")
  public RedirectView update(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    try {
      repos().apiResourceAdmin().update(id, readWrite(request));
      AdminRequests.notice(redirect, "Saved");
      return new RedirectView("/admin/api-resources/" + id, true);
    } catch (UncheckedSqlException | IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
      return new RedirectView("/admin/api-resources/" + id, true);
    }
  }

  @PostMapping("/admin/api-resources/{id}/delete")
  public RedirectView delete(@PathVariable int id, RedirectAttributes redirect) {
    repos().apiResourceAdmin().delete(id);
    AdminRequests.notice(redirect, "Deleted API resource " + id);
    return new RedirectView("/admin/api-resources", true);
  }

  @PostMapping("/admin/api-resources/{id}/secrets")
  public RedirectView addSecret(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    String value = request.getParameter("value");
    if (value == null || value.isBlank()) {
      AdminRequests.notice(redirect, "Secret value is required");
      return new RedirectView("/admin/api-resources/" + id + "?tab=secrets", true);
    }
    String type = request.getParameter("type");
    String hashType = request.getParameter("hashType");
    String description = request.getParameter("description");
    Instant expiration = parseExpiration(request.getParameter("expiration"));
    String hashed = SharedSecretHasher.hashForType(value.trim(), hashType);
    repos().apiResourceAdmin().addSecret(id, type, hashed, description, expiration);
    AdminRequests.notice(
        redirect,
        "Secret added ("
            + (hashType == null || hashType.isBlank() ? "Sha256" : hashType.trim())
            + "). Copy the plaintext below ??it will not be shown again.");
    AdminRequests.secretReveal(redirect, value.trim());
    return new RedirectView("/admin/api-resources/" + id + "?tab=secrets", true);
  }

  @PostMapping("/admin/api-resources/{id}/secrets/{secretId}/delete")
  public RedirectView deleteSecret(
      @PathVariable int id, @PathVariable int secretId, RedirectAttributes redirect) {
    repos().apiResourceAdmin().deleteSecret(id, secretId);
    AdminRequests.notice(redirect, "Secret deleted");
    return new RedirectView("/admin/api-resources/" + id + "?tab=secrets", true);
  }

  @PostMapping("/admin/api-resources/{id}/properties")
  public RedirectView addProperty(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    String key = request.getParameter("key");
    String value = request.getParameter("value");
    try {
      repos().apiResourceAdmin().addProperty(id, key, value);
      AdminRequests.notice(redirect, "Property added");
    } catch (IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
    }
    return new RedirectView("/admin/api-resources/" + id + "?tab=properties", true);
  }

  @PostMapping("/admin/api-resources/{id}/properties/{propertyId}/delete")
  public RedirectView deleteProperty(
      @PathVariable int id, @PathVariable int propertyId, RedirectAttributes redirect) {
    repos().apiResourceAdmin().deleteProperty(id, propertyId);
    AdminRequests.notice(redirect, "Property deleted");
    return new RedirectView("/admin/api-resources/" + id + "?tab=properties", true);
  }

  private String resourceForm(
      ApiResourceConfiguration existing, HttpServletRequest request, String initialTab) {
    boolean create = existing == null;
    String action = create ? "/admin/api-resources" : "/admin/api-resources/" + existing.id();
    String startTab =
        initialTab == null || initialTab.isBlank()
            ? "basic"
            : initialTab.trim().toLowerCase(java.util.Locale.ROOT);
    if (create && isSideOnlyTab(startTab)) {
      startTab = "basic";
    }
    String csrf =
        AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));

    String scopeText = create ? "" : AdminHtml.joinLines(existing.scopes());
    String claimText = create ? "" : AdminHtml.joinLines(existing.userClaims());
    String algorithmText =
        create
            ? ""
            : ApiResourceAdminRepository.algorithmsToLines(
                existing.allowedAccessTokenSigningAlgorithms());
    String[] scopePresets = scopePresets();

    StringBuilder html = new StringBuilder("<div class=\"editor\">");
    if (create) {
      html.append(
          "<p class=\"lead\">Configure an API resource (token audience). After create, manage Secrets and Properties on dedicated tabs.</p>");
    }

    html.append("<div class=\"tabs\" data-tabs data-initial-tab=\"")
        .append(AdminHtml.esc(startTab))
        .append("\">");
    html.append("<div class=\"tab-list\" role=\"tablist\" aria-label=\"API resource settings\">");
    html.append(tabButton("basic", "Basic", "basic".equals(startTab)));
    html.append(tabButton("scopes", "Scopes", "scopes".equals(startTab)));
    // tab id must not be "claims" ??clientEditorScript hides Save on Clients claims/secrets/properties.
    html.append(tabButton("userclaims", "User claims", "userclaims".equals(startTab)));
    if (!create) {
      html.append(tabButton("secrets", "Secrets", "secrets".equals(startTab)));
      html.append(tabButton("properties", "Properties", "properties".equals(startTab)));
    }
    html.append("</div>");

    html.append("<form method=\"post\" action=\"")
        .append(action)
        .append("\" data-client-settings>");
    html.append(csrf);

    // Basic ??matches Skoruba Api Resource edit fields
    html.append("<div class=\"tab-panel")
        .append("basic".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"basic\" role=\"tabpanel\">");
    html.append("<h2>Api resource</h2>");
    html.append("<div class=\"field\"><label>Name</label>");
    html.append(
        "<input name=\"name\" required autocomplete=\"off\" spellcheck=\"false\" placeholder=\"my_api\" value=\"");
    html.append(AdminHtml.esc(create ? "" : existing.name()));
    html.append(
        "\"><span class=\"hint\">Stable identifier. Access-token <code>aud</code> uses this value.</span></div>");
    html.append("<div class=\"grid-2\" style=\"margin-top:.75rem\">");
    html.append("<div class=\"field\"><label>Display name</label>");
    html.append("<input name=\"displayName\" placeholder=\"My API\" value=\"");
    html.append(AdminHtml.esc(create ? "" : existing.displayName()));
    html.append("\"></div>");
    html.append("<div class=\"field\"><label>Description</label>");
    html.append("<input name=\"description\" placeholder=\"Optional\" value=\"");
    html.append(AdminHtml.esc(create ? "" : existing.description()));
    html.append("\"></div></div>");
    html.append("<div class=\"checks\" style=\"margin-top:.85rem\">");
    html.append(
        AdminHtml.flagCheckbox(
            "showInDiscoveryDocument",
            "Show in discovery document",
            "When on, this API appears in the discovery document.",
            create || existing.showInDiscoveryDocument()));
    html.append(
        AdminHtml.flagCheckbox(
            "enabled",
            "Enabled",
            "Disabled resources are ignored for audience resolution.",
            create || existing.enabled()));
    html.append("</div>");
    if (!create) {
      html.append("<div class=\"btn-row\">");
      html.append(
          "<button type=\"button\" data-open-dialog=\"api-secret-dialog\">Add API secret</button>");
      html.append(
          "<button type=\"button\" data-tab-jump=\"properties\">Manage API resource properties</button>");
      html.append("</div>");
    }
    html.append(
        AdminHtml.listPicker(
            "allowedAccessTokenSigningAlgorithms",
            "Allowed access token signing algorithms",
            "Empty = use STS default. Stored as comma-separated in AllowedAccessTokenSigningAlgorithms.",
            algorithmText,
            ALGORITHM_PRESETS));
    html.append("</div>");

    // Scopes
    html.append("<div class=\"tab-panel")
        .append("scopes".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"scopes\" role=\"tabpanel\">");
    html.append("<h2>Scopes</h2>");
    html.append(
        AdminHtml.listPicker(
            "scopes",
            "API resource scopes",
            "Scope names associated with this API (ApiResourceScopes). Prefer names that exist under ApiScopes.",
            scopeText,
            scopePresets));
    html.append("</div>");

    // User claims (panel id userclaims ??keeps Save visible; see clientEditorScript)
    html.append("<div class=\"tab-panel")
        .append("userclaims".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"userclaims\" role=\"tabpanel\">");
    html.append("<h2>User claims</h2>");
    html.append(
        AdminHtml.listPicker(
            "userClaims",
            "User claim types",
            "Claim types included in access tokens for this API (ApiResourceClaims.Type).",
            claimText,
            CLAIM_PRESETS));
    html.append("</div>");

    html.append("<div class=\"form-actions\" data-settings-actions");
    if (isSideOnlyTab(startTab)) {
      html.append(" hidden");
    }
    html.append(">");
    html.append("<button type=\"submit\">")
        .append(create ? "Create API resource" : "Save changes")
        .append("</button>");
    html.append("<a href=\"/admin/api-resources\">Cancel</a>");
    html.append("</div>");
    html.append("</form>");

    if (!create) {
      html.append("<div class=\"tab-panel")
          .append("secrets".equals(startTab) ? " is-active" : "")
          .append("\" data-panel=\"secrets\" role=\"tabpanel\">");
      html.append(secretsPanel(existing, existing.id(), csrf, request));
      html.append("</div>");
      html.append("<div class=\"tab-panel")
          .append("properties".equals(startTab) ? " is-active" : "")
          .append("\" data-panel=\"properties\" role=\"tabpanel\">");
      html.append(propertiesPanel(existing, existing.id(), csrf));
      html.append("</div>");
      // Dialog lives outside hidden tab panels so showModal works from Basic.
      html.append(addSecretDialog(existing.id(), csrf));
    }

    html.append("</div>"); // tabs
    html.append("</div>"); // editor
    html.append(AdminHtml.clientEditorScript());
    html.append(secretDialogScript());
    return html.toString();
  }

  private String[] scopePresets() {
    try {
      List<String> names = repos().apiResourceAdmin().listApiScopeNames();
      if (names == null || names.isEmpty()) {
        return new String[] {"openid", "profile", "email", "roles", "offline_access"};
      }
      return names.toArray(String[]::new);
    } catch (RuntimeException e) {
      return new String[] {"openid", "profile", "email", "roles", "offline_access"};
    }
  }

  private static boolean isSideOnlyTab(String tab) {
    return "secrets".equals(tab) || "properties".equals(tab);
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

  private static String secretsPanel(
      ApiResourceConfiguration resource, int id, String csrf, HttpServletRequest request) {
    StringBuilder html = new StringBuilder();
    html.append("<h2>API secrets</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">IS4 <code>ApiResourceSecrets</code>: plaintext is hashed before save. "
            + "Used for introspection / reference-token style flows. Stored hashes are never shown again.</p>");
    String reveal = AdminRequests.flashAttr(request, "secretReveal");
    if (reveal != null && !reveal.isBlank()) {
      html.append("<div class=\"callout danger\"><strong>Copy this secret now.</strong><br>");
      html.append("You will not be able to retrieve it after leaving this page.<br>");
      html.append("<code>").append(AdminHtml.esc(reveal)).append("</code></div>");
    }
    html.append("<div class=\"btn-row\" style=\"margin:0 0 1rem\">");
    html.append(
        "<button type=\"button\" data-open-dialog=\"api-secret-dialog\">Add API secret</button>");
    html.append("</div>");

    html.append("<h2>Existing secrets</h2>");
    if (resource.secrets() == null || resource.secrets().isEmpty()) {
      html.append("<p class=\"hint\">No secrets yet. Use <strong>Add API secret</strong> to create one.</p>");
    } else {
      html.append(
          "<table><tr><th>Type</th><th>Description</th><th>Expiration</th><th>Created</th><th></th></tr>");
      for (var secret : resource.secrets()) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(secret.type()))
            .append("</td><td>")
            .append(AdminHtml.esc(secret.description() == null ? "" : secret.description()))
            .append("</td><td>")
            .append(AdminHtml.esc(formatInstant(secret.expiration())))
            .append("</td><td>")
            .append(AdminHtml.esc(formatInstant(secret.created())))
            .append("</td><td>");
        html.append("<form method=\"post\" action=\"/admin/api-resources/")
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
    html.append(
        "<div style=\"margin-top:1.5rem;padding-top:1rem;border-top:1px solid var(--line)\">");
    html.append("<h2>Danger zone</h2>");
    html.append("<form method=\"post\" action=\"/admin/api-resources/")
        .append(id)
        .append("/delete\"")
        .append(
            AdminHtml.dataConfirm(
                "Delete this API resource? This cannot be undone.",
                "Delete API resource",
                "Delete API resource",
                true))
        .append(">")
        .append(csrf)
        .append("<button type=\"submit\">Delete API resource</button></form>");
    html.append("</div>");
    return html.toString();
  }

  private static String addSecretDialog(int id, String csrf) {
    StringBuilder html = new StringBuilder();
    html.append("<dialog class=\"form-dialog\" id=\"api-secret-dialog\" aria-labelledby=\"api-secret-title\">");
    html.append("<div class=\"sheet\">");
    html.append("<h2 id=\"api-secret-title\">Add API secret</h2>");
    html.append(
        "<p class=\"lead hint\">Plaintext is hashed before save (Sha256 by default). Copy the value before you save ??it will not be shown again.</p>");
    html.append("<form method=\"post\" action=\"/admin/api-resources/")
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
        "<span class=\"hint\">Applies to Shared Secret only. Prefer Sha256 (IS4 default).</span>");
    html.append("</div></div>");
    html.append("<div class=\"field\" style=\"margin-top:.65rem\"><label>Secret value</label>");
    html.append("<div class=\"secret-row\">");
    html.append(
        "<input name=\"value\" id=\"secret-value\" type=\"text\" autocomplete=\"off\" spellcheck=\"false\" required placeholder=\"Paste or generate a secret\">");
    html.append(
        "<button type=\"button\" data-generate-secret title=\"Generate random secret\">Generate</button>");
    html.append("</div>");
    html.append(
        "<div class=\"callout warn\">Copy the new API secret value. You won't be able to retrieve it after you save.</div>");
    html.append("</div>");
    html.append("<div class=\"grid-2\">");
    html.append("<div class=\"field\"><label>Expiration</label>");
    html.append("<input type=\"date\" name=\"expiration\">");
    html.append("<span class=\"hint\">Optional. Leave blank for no expiry.</span></div>");
    html.append("<div class=\"field\"><label>Description</label>");
    html.append(
        "<input name=\"description\" type=\"text\" maxlength=\"200\" placeholder=\"e.g. rotated 2026-10\">");
    html.append("</div></div>");
    html.append("<div class=\"actions\">");
    html.append("<button type=\"button\" class=\"ghost\" data-close-dialog>Cancel</button>");
    html.append("<button type=\"submit\">Add API secret</button>");
    html.append("</div>");
    html.append("</form>");
    html.append("</div></dialog>");
    return html.toString();
  }

  private static String propertiesPanel(ApiResourceConfiguration resource, int id, String csrf) {
    StringBuilder html = new StringBuilder();
    html.append("<h2>API resource properties</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">IS4 <code>ApiResourceProperties</code> bag (key/value). "
            + "Used by custom code ??not OAuth protocol fields.</p>");
    html.append("<form method=\"post\" action=\"/admin/api-resources/")
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
    if (resource.properties() == null || resource.properties().isEmpty()) {
      html.append("<p class=\"hint\">No properties yet.</p>");
    } else {
      html.append("<table><tr><th>Key</th><th>Value</th><th></th></tr>");
      for (var property : resource.properties()) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(property.key()))
            .append("</td><td>")
            .append(AdminHtml.esc(property.value() == null ? "" : property.value()))
            .append("</td><td>");
        html.append("<form method=\"post\" action=\"/admin/api-resources/")
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

  private static String secretDialogScript() {
    return """
        <script>
        (function(){
          function generateSecret(){
            var bytes=new Uint8Array(32);
            crypto.getRandomValues(bytes);
            var bin='';
            for(var i=0;i<bytes.length;i++) bin+=String.fromCharCode(bytes[i]);
            return btoa(bin).replace(/\\+/g,'-').replace(/\\//g,'_').replace(/=+$/,'');
          }
          function openDialog(id){
            var d=document.getElementById(id);
            if(!d||typeof d.showModal!=='function') return;
            d.showModal();
            var input=d.querySelector('#secret-value,input[name=value]');
            if(input){ setTimeout(function(){ input.focus(); }, 30); }
          }
          function closeDialog(d){
            if(d&&typeof d.close==='function') d.close();
          }
          document.querySelectorAll('[data-open-dialog]').forEach(function(btn){
            btn.addEventListener('click',function(){
              openDialog(btn.getAttribute('data-open-dialog'));
            });
          });
          document.querySelectorAll('[data-tab-jump]').forEach(function(btn){
            btn.addEventListener('click',function(){
              var id=btn.getAttribute('data-tab-jump');
              document.querySelectorAll('[data-tabs]').forEach(function(root){
                if(typeof root._activateTab==='function') root._activateTab(id);
              });
            });
          });
          document.querySelectorAll('dialog.form-dialog').forEach(function(dlg){
            dlg.addEventListener('click',function(ev){
              if(ev.target===dlg) closeDialog(dlg);
            });
            dlg.querySelectorAll('[data-close-dialog]').forEach(function(btn){
              btn.addEventListener('click',function(){ closeDialog(dlg); });
            });
          });
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

  private ApiResourceWrite readWrite(HttpServletRequest request) {
    String name = request.getParameter("name");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    return new ApiResourceWrite(
        name.trim(),
        Optional.ofNullable(request.getParameter("displayName")).orElse(""),
        Optional.ofNullable(request.getParameter("description")).orElse(""),
        AdminRequests.checked(request, "enabled"),
        AdminRequests.checked(request, "showInDiscoveryDocument"),
        ApiResourceAdminRepository.linesToAlgorithms(
            AdminHtml.splitLines(request.getParameter("allowedAccessTokenSigningAlgorithms"))),
        AdminHtml.splitLines(request.getParameter("scopes")),
        AdminHtml.splitLines(request.getParameter("userClaims")));
  }

  private JdbcRepositories repos() {
    return jdbc.orElseThrow(
        () -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "database not configured"));
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
