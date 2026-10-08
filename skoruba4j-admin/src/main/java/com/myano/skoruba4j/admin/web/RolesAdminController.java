package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.identity.IdentityRole;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.RoleClaim;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import jakarta.servlet.http.HttpServletRequest;
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
public class RolesAdminController {
  private final Optional<JdbcRepositories> jdbc;

  public RolesAdminController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/admin/roles", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String searchText,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    PageQuery query = PageQuery.of(searchText, page, 20);
    StringBuilder body =
        new StringBuilder("<h1>").append(AdminHtml.esc(Messages.t("roles.title"))).append("</h1>");
    body.append(
        "<p class=\"lead\">ASP.NET Identity roles. Edit name, claims, and see assigned users.</p>");
    body.append("<p><a href=\"/admin/roles/new\">New role</a></p>");
    body.append(AdminHtml.searchBar("/admin/roles", query.searchText(), query.page()));
    PageResult<IdentityRole> result = repos().roles().search(query);
    body.append("<table><tr><th>Name</th><th>Normalized</th><th>Id</th></tr>");
    for (IdentityRole role : result.items()) {
      body.append("<tr><td><a href=\"/admin/roles/")
          .append(AdminHtml.esc(role.id()))
          .append("\">")
          .append(AdminHtml.esc(role.name()))
          .append("</a></td><td>")
          .append(AdminHtml.esc(role.normalizedName()))
          .append("</td><td><code>")
          .append(AdminHtml.esc(role.id()))
          .append("</code></td></tr>");
    }
    body.append("</table>");
    body.append(
        AdminHtml.pager(
            "/admin/roles", query.searchText(), result.page(), result.pageSize(), result.totalCount()));
    return page(request, Messages.t("roles.title"), body.toString());
  }

  @GetMapping(value = "/admin/roles/new", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String createForm(HttpServletRequest request) {
    return page(request, "New role", "<h1>New role</h1>" + roleForm(null, request, "basic"));
  }

  @GetMapping(value = "/admin/roles/{id}", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String detail(HttpServletRequest request, @PathVariable String id) {
    IdentityRole role =
        repos().roles().findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String tab = request.getParameter("tab");
    if (tab == null || tab.isBlank()) {
      tab = "basic";
    }
    StringBuilder body =
        new StringBuilder("<h1>Role ").append(AdminHtml.esc(role.name())).append("</h1>");
    body.append(roleForm(role, request, tab));
    return page(request, role.name(), body.toString());
  }

  @PostMapping("/admin/roles")
  public RedirectView create(HttpServletRequest request, RedirectAttributes redirect) {
    try {
      String name = request.getParameter("name");
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("name is required");
      }
      String id = repos().roles().insert(name);
      AdminRequests.notice(redirect, "Role created");
      return new RedirectView("/admin/roles/" + id, true);
    } catch (UncheckedSqlException | IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
      return new RedirectView("/admin/roles/new", true);
    }
  }

  @PostMapping("/admin/roles/{id}")
  public RedirectView update(
      @PathVariable String id, HttpServletRequest request, RedirectAttributes redirect) {
    try {
      String name = request.getParameter("name");
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("name is required");
      }
      repos().roles().update(id, name);
      AdminRequests.notice(redirect, "Saved");
    } catch (UncheckedSqlException | IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
    }
    return new RedirectView("/admin/roles/" + id, true);
  }

  @PostMapping("/admin/roles/{id}/delete")
  public RedirectView delete(@PathVariable String id, RedirectAttributes redirect) {
    repos().roles().delete(id);
    AdminRequests.notice(redirect, "Role deleted");
    return new RedirectView("/admin/roles", true);
  }

  @PostMapping("/admin/roles/{id}/claims")
  public RedirectView addClaim(
      @PathVariable String id, HttpServletRequest request, RedirectAttributes redirect) {
    try {
      repos()
          .roles()
          .addClaim(id, request.getParameter("type"), request.getParameter("value"));
      AdminRequests.notice(redirect, "Claim added");
    } catch (IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
    }
    return new RedirectView("/admin/roles/" + id + "?tab=claims", true);
  }

  @PostMapping("/admin/roles/{id}/claims/{claimId}/delete")
  public RedirectView deleteClaim(
      @PathVariable String id, @PathVariable int claimId, RedirectAttributes redirect) {
    repos().roles().deleteClaim(id, claimId);
    AdminRequests.notice(redirect, "Claim deleted");
    return new RedirectView("/admin/roles/" + id + "?tab=claims", true);
  }

  @PostMapping("/admin/roles/{id}/users/{userId}/delete")
  public RedirectView removeUser(
      @PathVariable String id, @PathVariable String userId, RedirectAttributes redirect) {
    repos().users().removeRole(userId, id);
    AdminRequests.notice(redirect, "User removed from role");
    return new RedirectView("/admin/roles/" + id + "?tab=users", true);
  }

  private String roleForm(IdentityRole existing, HttpServletRequest request, String initialTab) {
    boolean create = existing == null;
    String action = create ? "/admin/roles" : "/admin/roles/" + existing.id();
    String startTab =
        initialTab == null || initialTab.isBlank()
            ? "basic"
            : initialTab.trim().toLowerCase(java.util.Locale.ROOT);
    if (create && !"basic".equals(startTab)) {
      startTab = "basic";
    }
    String csrf =
        AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));

    StringBuilder html = new StringBuilder("<div class=\"editor\">");
    html.append("<div class=\"tabs\" data-tabs data-initial-tab=\"")
        .append(AdminHtml.esc(startTab))
        .append("\">");
    html.append("<div class=\"tab-list\" role=\"tablist\">");
    html.append(tabButton("basic", "Basic", "basic".equals(startTab)));
    if (!create) {
      html.append(tabButton("claims", "Claims", "claims".equals(startTab)));
      html.append(tabButton("users", "Users", "users".equals(startTab)));
    }
    html.append("</div>");

    html.append("<form method=\"post\" action=\"")
        .append(action)
        .append("\" data-client-settings>");
    html.append(csrf);
    html.append("<div class=\"tab-panel")
        .append("basic".equals(startTab) ? " is-active" : "")
        .append("\" data-panel=\"basic\" role=\"tabpanel\">");
    html.append("<h2>Role</h2>");
    html.append("<div class=\"field\"><label>Name</label>");
    html.append("<input name=\"name\" required autocomplete=\"off\" spellcheck=\"false\" value=\"");
    html.append(AdminHtml.esc(create ? "" : existing.name()));
    html.append(
        "\"><span class=\"hint\">Normalized name is stored uppercase for Identity lookups.</span></div>");
    if (!create) {
      html.append("<p class=\"hint\" style=\"margin:.75rem 0 0\">Id: <code>")
          .append(AdminHtml.esc(existing.id()))
          .append("</code></p>");
    }
    html.append("</div>");

    html.append("<div class=\"form-actions\" data-settings-actions");
    if ("claims".equals(startTab) || "users".equals(startTab)) {
      html.append(" hidden");
    }
    html.append(">");
    html.append("<button type=\"submit\">")
        .append(create ? "Create role" : "Save changes")
        .append("</button>");
    html.append("<a href=\"/admin/roles\">Cancel</a>");
    html.append("</div></form>");

    if (!create) {
      html.append("<div class=\"tab-panel")
          .append("claims".equals(startTab) ? " is-active" : "")
          .append("\" data-panel=\"claims\" role=\"tabpanel\">");
      html.append(claimsPanel(existing, csrf));
      html.append("</div>");
      html.append("<div class=\"tab-panel")
          .append("users".equals(startTab) ? " is-active" : "")
          .append("\" data-panel=\"users\" role=\"tabpanel\">");
      html.append(usersPanel(existing, csrf));
      html.append("</div>");
    }

    html.append("</div></div>");
    html.append(AdminHtml.clientEditorScript());
    return html.toString();
  }

  private String claimsPanel(IdentityRole role, String csrf) {
    StringBuilder html = new StringBuilder();
    html.append("<h2>Role claims</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">Identity <code>RoleClaims</code> (ClaimType / ClaimValue).</p>");
    html.append("<form method=\"post\" action=\"/admin/roles/")
        .append(AdminHtml.esc(role.id()))
        .append("/claims\">")
        .append(csrf);
    html.append("<div class=\"grid-2\">");
    html.append(
        "<div class=\"field\"><label>Type</label><input name=\"type\" required autocomplete=\"off\" placeholder=\"permission\"></div>");
    html.append(
        "<div class=\"field\"><label>Value</label><input name=\"value\" autocomplete=\"off\" placeholder=\"read\"></div>");
    html.append("</div>");
    html.append("<p style=\"margin:.9rem 0 0\"><button type=\"submit\">Add claim</button></p>");
    html.append("</form>");
    List<RoleClaim> claims = repos().roles().listClaims(role.id());
    html.append("<h2 style=\"margin-top:1.35rem\">Existing claims</h2>");
    if (claims.isEmpty()) {
      html.append("<p class=\"hint\">No claims yet.</p>");
    } else {
      html.append("<table><tr><th>Type</th><th>Value</th><th></th></tr>");
      for (RoleClaim claim : claims) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(claim.type()))
            .append("</td><td>")
            .append(AdminHtml.esc(claim.value() == null ? "" : claim.value()))
            .append("</td><td>");
        html.append("<form method=\"post\" action=\"/admin/roles/")
            .append(AdminHtml.esc(role.id()))
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

  private String usersPanel(IdentityRole role, String csrf) {
    StringBuilder html = new StringBuilder();
    html.append("<h2>Users in role</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">From <code>UserRoles</code>. Assign roles on the user editor.</p>");
    List<IdentityUser> users = repos().roles().listUsers(role.id(), 100);
    if (users.isEmpty()) {
      html.append("<p class=\"hint\">No users assigned.</p>");
    } else {
      html.append("<table><tr><th>UserName</th><th>Email</th><th></th></tr>");
      for (IdentityUser user : users) {
        html.append("<tr><td><a href=\"/admin/users/")
            .append(AdminHtml.esc(user.id()))
            .append("\">")
            .append(AdminHtml.esc(user.userName()))
            .append("</a></td><td>")
            .append(AdminHtml.esc(user.email() == null ? "" : user.email()))
            .append("</td><td>");
        html.append("<form method=\"post\" action=\"/admin/roles/")
            .append(AdminHtml.esc(role.id()))
            .append("/users/")
            .append(AdminHtml.esc(user.id()))
            .append("/delete\"")
            .append(AdminHtml.dataConfirm("Remove this user from the role?"))
            .append(">")
            .append(csrf)
            .append("<button type=\"submit\">Remove</button></form></td></tr>");
      }
      html.append("</table>");
    }
    html.append(
        "<div style=\"margin-top:1.5rem;padding-top:1rem;border-top:1px solid var(--line)\">");
    html.append("<h2>Danger zone</h2>");
    html.append("<form method=\"post\" action=\"/admin/roles/")
        .append(AdminHtml.esc(role.id()))
        .append("/delete\"")
        .append(
            AdminHtml.dataConfirm(
                "Delete this role? User assignments and role claims are removed.",
                "Delete role",
                "Delete role",
                true))
        .append(">")
        .append(csrf)
        .append("<button type=\"submit\">Delete role</button></form></div>");
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
