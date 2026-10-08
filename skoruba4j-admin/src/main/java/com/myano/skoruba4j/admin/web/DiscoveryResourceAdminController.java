package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.ApiResourceSummary;
import com.myano.skoruba4j.domain.configstore.DiscoveryResourceAdminRepository;
import com.myano.skoruba4j.domain.configstore.DiscoveryResourceConfiguration;
import com.myano.skoruba4j.domain.configstore.DiscoveryResourceWrite;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import com.myano.skoruba4j.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Skoruba-style edit for ApiScopes / IdentityResources: Basic flags + user claims + properties.
 */
abstract class DiscoveryResourceAdminController {
  private static final String[] CLAIM_PRESETS = {
    "role", "name", "email", "sub", "preferred_username", "phone_number", "address", "profile"
  };

  private final Optional<JdbcRepositories> jdbc;
  private final Function<JdbcRepositories, DiscoveryResourceAdminRepository> repo;
  private final String path;
  private final String titleKey;
  private final String singularKey;

  DiscoveryResourceAdminController(
      Optional<JdbcRepositories> jdbc,
      Function<JdbcRepositories, DiscoveryResourceAdminRepository> repo,
      String path,
      String titleKey,
      String singularKey) {
    this.jdbc = jdbc;
    this.repo = repo;
    this.path = path;
    this.titleKey = titleKey;
    this.singularKey = singularKey;
  }

  private String title() {
    return Messages.t(titleKey);
  }

  private String singular() {
    return Messages.t(singularKey);
  }

  @GetMapping(produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String searchText,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    PageQuery query = PageQuery.of(searchText, page, 20);
    StringBuilder body = new StringBuilder("<h1>").append(AdminHtml.esc(title())).append("</h1>");
    body.append("<p><a href=\"")
        .append(path)
        .append("/new\">New ")
        .append(AdminHtml.esc(singular()))
        .append("</a></p>");
    body.append(AdminHtml.searchBar(path, query.searchText(), query.page()));
    PageResult<ApiResourceSummary> result = repo().search(query);
    body.append("<table><tr><th>Id</th><th>Name</th><th>DisplayName</th><th>Enabled</th></tr>");
    for (ApiResourceSummary item : result.items()) {
      body.append("<tr><td>")
          .append(item.id())
          .append("</td><td><a href=\"")
          .append(path)
          .append("/")
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
        AdminHtml.pager(path, query.searchText(), result.page(), result.pageSize(), result.totalCount()));
    return page(request, title(), body.toString());
  }

  @GetMapping(value = "/new", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String createForm(HttpServletRequest request) {
    return page(
        request,
        "New " + singular(),
        "<h1>New " + AdminHtml.esc(singular()) + "</h1>" + resourceForm(null, request, "basic"));
  }

  @GetMapping(value = "/{id}", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String detail(HttpServletRequest request, @PathVariable int id) {
    DiscoveryResourceConfiguration resource =
        repo().findFull(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String tab = request.getParameter("tab");
    if (tab == null || tab.isBlank()) {
      tab = "basic";
    }
    StringBuilder body =
        new StringBuilder("<h1>")
            .append(AdminHtml.esc(singular()))
            .append(' ')
            .append(AdminHtml.esc(resource.name()))
            .append("</h1>");
    body.append(resourceForm(resource, request, tab));
    return page(request, resource.name(), body.toString());
  }

  @PostMapping
  public RedirectView create(HttpServletRequest request, RedirectAttributes redirect) {
    try {
      int id = repo().insert(readWrite(request));
      AdminRequests.notice(redirect, "Created " + singular() + " " + id);
      return new RedirectView(path + "/" + id, true);
    } catch (UncheckedSqlException | IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
      return new RedirectView(path + "/new", true);
    }
  }

  @PostMapping("/{id}")
  public RedirectView update(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    try {
      repo().update(id, readWrite(request));
      AdminRequests.notice(redirect, "Saved");
    } catch (UncheckedSqlException | IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
    }
    return new RedirectView(path + "/" + id, true);
  }

  @PostMapping("/{id}/delete")
  public RedirectView delete(@PathVariable int id, RedirectAttributes redirect) {
    repo().delete(id);
    AdminRequests.notice(redirect, "Deleted " + singular() + " " + id);
    return new RedirectView(path, true);
  }

  @PostMapping("/{id}/properties")
  public RedirectView addProperty(
      @PathVariable int id, HttpServletRequest request, RedirectAttributes redirect) {
    try {
      repo().addProperty(id, request.getParameter("key"), request.getParameter("value"));
      AdminRequests.notice(redirect, "Property added");
    } catch (IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
    }
    return new RedirectView(path + "/" + id + "?tab=properties", true);
  }

  @PostMapping("/{id}/properties/{propertyId}/delete")
  public RedirectView deleteProperty(
      @PathVariable int id, @PathVariable int propertyId, RedirectAttributes redirect) {
    repo().deleteProperty(id, propertyId);
    AdminRequests.notice(redirect, "Property deleted");
    return new RedirectView(path + "/" + id + "?tab=properties", true);
  }

  private String resourceForm(
      DiscoveryResourceConfiguration existing, HttpServletRequest request, String initialTab) {
    boolean create = existing == null;
    String action = create ? path : path + "/" + existing.id();
    String startTab =
        initialTab == null || initialTab.isBlank()
            ? "basic"
            : initialTab.trim().toLowerCase(java.util.Locale.ROOT);
    if (create && "properties".equals(startTab)) {
      startTab = "basic";
    }
    String csrf =
        AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));
    String claimText = create ? "" : AdminHtml.joinLines(existing.userClaims());

    StringBuilder html = new StringBuilder("<div class=\"editor\">");
    html.append("<div class=\"tabs\" data-tabs data-initial-tab=\"")
        .append(AdminHtml.esc(startTab))
        .append("\">");
    html.append("<div class=\"tab-list\" role=\"tablist\">");
    html.append(tabButton("basic", "Basic", "basic".equals(startTab)));
    html.append(tabButton("userclaims", "User claims", "userclaims".equals(startTab)));
    if (!create) {
      html.append(tabButton("properties", "Properties", "properties".equals(startTab)));
    }
    html.append("</div>");

    html.append("<form method=\"post\" action=\"")
        .append(action)
        .append("\" data-client-settings>");
    html.append(csrf);

    html.append("<div class=\"tab-panel")
        .append("basic".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"basic\" role=\"tabpanel\">");
    html.append("<h2>").append(AdminHtml.esc(singular())).append("</h2>");
    html.append("<div class=\"field\"><label>Name</label>");
    html.append("<input name=\"name\" required autocomplete=\"off\" spellcheck=\"false\" value=\"");
    html.append(AdminHtml.esc(create ? "" : existing.name()));
    html.append("\"></div>");
    html.append("<div class=\"grid-2\" style=\"margin-top:.75rem\">");
    html.append("<div class=\"field\"><label>Display name</label>");
    html.append("<input name=\"displayName\" value=\"");
    html.append(AdminHtml.esc(create ? "" : existing.displayName()));
    html.append("\"></div>");
    html.append("<div class=\"field\"><label>Description</label>");
    html.append("<input name=\"description\" value=\"");
    html.append(AdminHtml.esc(create ? "" : existing.description()));
    html.append("\"></div></div>");
    html.append("<div class=\"checks\" style=\"margin-top:.85rem\">");
    html.append(
        AdminHtml.flagCheckbox(
            "enabled",
            "Enabled",
            "Disabled resources are ignored by discovery / consent.",
            create || existing.enabled()));
    html.append(
        AdminHtml.flagCheckbox(
            "showInDiscoveryDocument",
            "Show in discovery document",
            "When on, appears in the OpenID discovery document.",
            create || existing.showInDiscoveryDocument()));
    html.append(
        AdminHtml.flagCheckbox(
            "required",
            "Required",
            "Consent UI marks this scope / resource as required.",
            !create && existing.required()));
    html.append(
        AdminHtml.flagCheckbox(
            "emphasize",
            "Emphasize",
            "Consent UI emphasizes this scope / resource.",
            !create && existing.emphasize()));
    html.append("</div>");
    if (!create) {
      html.append("<div class=\"btn-row\">");
      html.append(
          "<button type=\"button\" data-tab-jump=\"properties\">Manage properties</button>");
      html.append("</div>");
    }
    html.append("</div>");

    html.append("<div class=\"tab-panel")
        .append("userclaims".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"userclaims\" role=\"tabpanel\">");
    html.append("<h2>User claims</h2>");
    html.append(
        AdminHtml.listPicker(
            "userClaims",
            "User claim types",
            "Claim types associated with this resource (one per line).",
            claimText,
            CLAIM_PRESETS));
    html.append("</div>");

    html.append("<div class=\"form-actions\" data-settings-actions");
    if ("properties".equals(startTab)) {
      html.append(" hidden");
    }
    html.append(">");
    html.append("<button type=\"submit\">")
        .append(create ? "Create" : "Save changes")
        .append("</button>");
    html.append("<a href=\"").append(path).append("\">Cancel</a>");
    html.append("</div></form>");

    if (!create) {
      html.append("<div class=\"tab-panel")
          .append("properties".equals(startTab) ? " is-active" : "")
          .append("\" data-panel=\"properties\" role=\"tabpanel\">");
      html.append(propertiesPanel(existing, csrf));
      html.append("</div>");
    }

    html.append("</div></div>");
    html.append(AdminHtml.clientEditorScript());
    html.append(tabJumpScript());
    return html.toString();
  }

  private String propertiesPanel(DiscoveryResourceConfiguration resource, String csrf) {
    StringBuilder html = new StringBuilder();
    html.append("<h2>Properties</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">Key/value bag (not OAuth protocol fields).</p>");
    html.append("<form method=\"post\" action=\"")
        .append(path)
        .append("/")
        .append(resource.id())
        .append("/properties\">")
        .append(csrf);
    html.append("<div class=\"grid-2\">");
    html.append(
        "<div class=\"field\"><label>Key</label><input name=\"key\" required autocomplete=\"off\"></div>");
    html.append(
        "<div class=\"field\"><label>Value</label><input name=\"value\" autocomplete=\"off\"></div>");
    html.append("</div>");
    html.append("<p style=\"margin:.9rem 0 0\"><button type=\"submit\">Add property</button></p>");
    html.append("</form>");
    html.append("<h2 style=\"margin-top:1.35rem\">Existing properties</h2>");
    if (resource.properties().isEmpty()) {
      html.append("<p class=\"hint\">No properties yet.</p>");
    } else {
      html.append("<table><tr><th>Key</th><th>Value</th><th></th></tr>");
      for (var property : resource.properties()) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(property.key()))
            .append("</td><td>")
            .append(AdminHtml.esc(property.value() == null ? "" : property.value()))
            .append("</td><td>");
        html.append("<form method=\"post\" action=\"")
            .append(path)
            .append("/")
            .append(resource.id())
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
    html.append(
        "<div style=\"margin-top:1.5rem;padding-top:1rem;border-top:1px solid var(--line)\">");
    html.append("<h2>Danger zone</h2>");
    html.append("<form method=\"post\" action=\"")
        .append(path)
        .append("/")
        .append(resource.id())
        .append("/delete\"")
        .append(
            AdminHtml.dataConfirm(
                "Delete this " + singular() + "? This cannot be undone.",
                "Delete " + singular(),
                "Delete",
                true))
        .append(">")
        .append(csrf)
        .append("<button type=\"submit\">Delete ")
        .append(AdminHtml.esc(singular()))
        .append("</button></form></div>");
    return html.toString();
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

  private static String tabJumpScript() {
    return """
        <script>
        (function(){
          document.querySelectorAll('[data-tab-jump]').forEach(function(btn){
            btn.addEventListener('click',function(){
              var id=btn.getAttribute('data-tab-jump');
              document.querySelectorAll('[data-tabs]').forEach(function(root){
                if(typeof root._activateTab==='function') root._activateTab(id);
              });
            });
          });
        })();
        </script>
        """;
  }

  private DiscoveryResourceWrite readWrite(HttpServletRequest request) {
    String name = request.getParameter("name");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    return new DiscoveryResourceWrite(
        name.trim(),
        Optional.ofNullable(request.getParameter("displayName")).orElse(""),
        Optional.ofNullable(request.getParameter("description")).orElse(""),
        AdminRequests.checked(request, "enabled"),
        AdminRequests.checked(request, "showInDiscoveryDocument"),
        AdminRequests.checked(request, "required"),
        AdminRequests.checked(request, "emphasize"),
        AdminHtml.splitLines(request.getParameter("userClaims")));
  }

  private DiscoveryResourceAdminRepository repo() {
    return repo.apply(repos());
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
