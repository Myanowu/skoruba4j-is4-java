package com.myano.skoruba4j.adminapi.web.ui;

import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.ApiResourceSummary;
import com.myano.skoruba4j.domain.configstore.ClientSummary;
import com.myano.skoruba4j.domain.configstore.PersistedGrantRecord;
import com.myano.skoruba4j.domain.identity.IdentityRole;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** Read-only list pages (MVP). Edit/create follows in later slices. */
@Controller
@ConditionalOnProperty(
    prefix = "idserver.admin",
    name = "api-ui-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class UiCatalogControllers {
  private final Optional<JdbcRepositories> jdbc;

  public UiCatalogControllers(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/ui/clients", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String clients(@RequestParam(required = false) String q) {
    PageResult<ClientSummary> page = repos().clients().search(PageQuery.of(q, 1, 50));
    StringBuilder body = new StringBuilder();
    body.append("<h1>Clients</h1>");
    body.append("<p class=\"muted\">JSON: <code>GET /api/Clients</code></p>");
    body.append(searchForm("/ui/clients", q));
    body.append(
        "<table><thead><tr><th>Id</th><th>ClientId</th><th>Name</th><th>Enabled</th></tr></thead><tbody>");
    for (ClientSummary c : page.items()) {
      body.append("<tr><td>")
          .append(c.id())
          .append("</td><td><code>")
          .append(ApiUiHtml.esc(c.clientId()))
          .append("</code></td><td>")
          .append(ApiUiHtml.esc(c.clientName()))
          .append("</td><td>")
          .append(c.enabled())
          .append("</td></tr>");
    }
    body.append("</tbody></table>");
    body.append("<p class=\"muted\">Showing page ")
        .append(page.page())
        .append(" · total ")
        .append(page.totalCount())
        .append("</p>");
    return ApiUiHtml.page("Clients", null, body.toString(), "clients", ApiDebugPresets.clients());
  }

  @GetMapping(value = "/ui/users", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String users(@RequestParam(required = false) String q) {
    PageResult<IdentityUser> page = repos().users().search(PageQuery.of(q, 1, 50));
    StringBuilder body = new StringBuilder();
    body.append("<h1>Users</h1>");
    body.append("<p class=\"muted\">JSON: <code>GET /api/Users</code></p>");
    body.append(searchForm("/ui/users", q));
    body.append("<table><thead><tr><th>UserName</th><th>Email</th><th>Id</th></tr></thead><tbody>");
    for (IdentityUser u : page.items()) {
      body.append("<tr><td>")
          .append(ApiUiHtml.esc(u.userName()))
          .append("</td><td>")
          .append(ApiUiHtml.esc(u.email()))
          .append("</td><td><code>")
          .append(ApiUiHtml.esc(u.id()))
          .append("</code></td></tr>");
    }
    body.append("</tbody></table>");
    return ApiUiHtml.page("Users", null, body.toString(), "users", ApiDebugPresets.users());
  }

  @GetMapping(value = "/ui/roles", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String roles(@RequestParam(required = false) String q) {
    PageResult<IdentityRole> page = repos().roles().search(PageQuery.of(q, 1, 50));
    StringBuilder body = new StringBuilder();
    body.append("<h1>Roles</h1>");
    body.append("<p class=\"muted\">JSON: <code>GET /api/Roles</code></p>");
    body.append(searchForm("/ui/roles", q));
    body.append("<table><thead><tr><th>Name</th><th>Id</th></tr></thead><tbody>");
    for (IdentityRole r : page.items()) {
      body.append("<tr><td>")
          .append(ApiUiHtml.esc(r.name()))
          .append("</td><td><code>")
          .append(ApiUiHtml.esc(r.id()))
          .append("</code></td></tr>");
    }
    body.append("</tbody></table>");
    return ApiUiHtml.page("Roles", null, body.toString(), "roles", ApiDebugPresets.roles());
  }

  @GetMapping(value = "/ui/api-resources", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String apiResources(@RequestParam(required = false) String q) {
    return summaryPage(
        "ApiResources",
        "api-resources",
        "/api/ApiResources",
        "/ui/api-resources",
        q,
        repos().apiResourceAdmin().search(PageQuery.of(q, 1, 50)));
  }

  @GetMapping(value = "/ui/api-scopes", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String apiScopes(@RequestParam(required = false) String q) {
    return summaryPage(
        "ApiScopes",
        "api-scopes",
        "/api/ApiScopes",
        "/ui/api-scopes",
        q,
        repos().apiScopes().search(PageQuery.of(q, 1, 50)));
  }

  @GetMapping(value = "/ui/identity-resources", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String identityResources(@RequestParam(required = false) String q) {
    return summaryPage(
        "IdentityResources",
        "identity-resources",
        "/api/IdentityResources",
        "/ui/identity-resources",
        q,
        repos().identityResources().search(PageQuery.of(q, 1, 50)));
  }

  @GetMapping(value = "/ui/grants", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String grants(@RequestParam(required = false) String q) {
    PageResult<PersistedGrantRecord> page =
        repos().persistedGrants().search(PageQuery.of(q, 1, 50));
    StringBuilder body = new StringBuilder();
    body.append("<h1>Persisted grants</h1>");
    body.append("<p class=\"muted\">JSON: <code>GET /api/PersistedGrants</code></p>");
    body.append(searchForm("/ui/grants", q));
    body.append(
        "<table><thead><tr><th>Type</th><th>Subject</th><th>Client</th><th>Key</th></tr></thead><tbody>");
    for (PersistedGrantRecord g : page.items()) {
      body.append("<tr><td>")
          .append(ApiUiHtml.esc(g.type()))
          .append("</td><td>")
          .append(ApiUiHtml.esc(g.subjectId()))
          .append("</td><td>")
          .append(ApiUiHtml.esc(g.clientId()))
          .append("</td><td><code>")
          .append(ApiUiHtml.esc(g.key()))
          .append("</code></td></tr>");
    }
    body.append("</tbody></table>");
    return ApiUiHtml.page("Grants", null, body.toString(), "grants", ApiDebugPresets.grants());
  }

  private static String summaryPage(
      String title,
      String sectionKey,
      String apiPath,
      String uiPath,
      String q,
      PageResult<ApiResourceSummary> page) {
    StringBuilder body = new StringBuilder();
    body.append("<h1>").append(ApiUiHtml.esc(title)).append("</h1>");
    body.append("<p class=\"muted\">JSON: <code>GET ")
        .append(ApiUiHtml.esc(apiPath))
        .append("</code></p>");
    body.append(searchForm(uiPath, q));
    body.append(
        "<table><thead><tr><th>Id</th><th>Name</th><th>Display</th><th>Enabled</th></tr></thead><tbody>");
    for (ApiResourceSummary item : page.items()) {
      body.append("<tr><td>")
          .append(item.id())
          .append("</td><td><code>")
          .append(ApiUiHtml.esc(item.name()))
          .append("</code></td><td>")
          .append(ApiUiHtml.esc(item.displayName()))
          .append("</td><td>")
          .append(item.enabled())
          .append("</td></tr>");
    }
    body.append("</tbody></table>");
    return ApiUiHtml.page(
        title, null, body.toString(), sectionKey, ApiDebugPresets.named(apiPath));
  }

  private static String searchForm(String action, String q) {
    return "<form method=\"get\" action=\""
        + ApiUiHtml.esc(action)
        + "\" style=\"margin:.75rem 0\">"
        + "<input type=\"search\" name=\"q\" value=\""
        + ApiUiHtml.esc(q == null ? "" : q)
        + "\" placeholder=\"Search\">"
        + " <button type=\"submit\">Search</button></form>";
  }

  private JdbcRepositories repos() {
    return jdbc.orElseThrow(
        () ->
            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "database not configured"));
  }
}
