package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.identity.IdentityRole;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.UserClaim;
import com.myano.skoruba4j.domain.identity.UserLogin;
import com.myano.skoruba4j.domain.identity.UserProfileWrite;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
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

/**
 * Identity user admin (Skoruba User Profile style): Profile / Roles / Claims / Providers / Password
 * tabs. Self-drawn HTML ??not Skoruba cshtml.
 */
@Controller
public class UsersAdminController {
  private static final DateTimeFormatter LOCKOUT_LOCAL =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private final Optional<JdbcRepositories> jdbc;
  private final IdentityPasswordHasher hasher;

  public UsersAdminController(Optional<JdbcRepositories> jdbc, IdentityPasswordHasher hasher) {
    this.jdbc = jdbc;
    this.hasher = hasher;
  }

  @GetMapping(value = "/admin/users", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String searchText,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    PageQuery query = PageQuery.of(searchText, page, 20);
    StringBuilder body =
        new StringBuilder("<h1>").append(AdminHtml.esc(Messages.t("users.title"))).append("</h1>");
    body.append("<p><a class=\"button\" href=\"/admin/users/new\">New user</a></p>");
    body.append(AdminHtml.searchBar("/admin/users", query.searchText(), query.page()));
    PageResult<IdentityUser> result = repos().users().search(query);
    body.append(
        "<table><tr><th>UserName</th><th>Email</th><th>Lockout</th><th>2FA</th><th>Id</th></tr>");
    for (IdentityUser u : result.items()) {
      body.append("<tr><td><a href=\"/admin/users/")
          .append(AdminHtml.esc(u.id()))
          .append("\">")
          .append(AdminHtml.esc(u.userName()))
          .append("</a></td><td>")
          .append(AdminHtml.esc(u.email()))
          .append("</td><td>")
          .append(u.lockoutEnabled() ? "on" : "off")
          .append("</td><td>")
          .append(u.twoFactorEnabled() ? "on" : "off")
          .append("</td><td><code>")
          .append(AdminHtml.esc(u.id()))
          .append("</code></td></tr>");
    }
    body.append("</table>");
    body.append(
        AdminHtml.pager(
            "/admin/users", query.searchText(), result.page(), result.pageSize(), result.totalCount()));
    return page(request, Messages.t("users.title"), body.toString());
  }

  @GetMapping(value = "/admin/users/new", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String createForm(HttpServletRequest request) {
    String tab = tabParam(request, "profile");
    return page(request, "New user", "<h1>New user</h1>" + userEditor(null, request, tab));
  }

  @GetMapping(value = "/admin/users/{id}", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String detail(HttpServletRequest request, @PathVariable String id) {
    IdentityUser u =
        repos().users().findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String tab = tabParam(request, "profile");
    String title = u.userName() == null || u.userName().isBlank() ? id : u.userName();
    String csrf =
        AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));
    StringBuilder body = new StringBuilder("<p class=\"crumbs\"><a href=\"/admin/users\">Users</a> / ");
    body.append(AdminHtml.esc(title)).append("</p>");
    body.append("<div class=\"page-head\">");
    body.append("<h1>").append(AdminHtml.esc(title)).append("</h1>");
    body.append("<div class=\"page-actions\">");
    body.append("<form method=\"post\" action=\"/admin/users/")
        .append(AdminHtml.esc(id))
        .append("/delete\"")
        .append(AdminHtml.dataConfirm("Delete this user? This cannot be undone.", "Delete user", "Delete user", true))
        .append(">")
        .append(csrf)
        .append("<button type=\"submit\" class=\"danger\">Delete user</button></form>");
    body.append("</div></div>");
    body.append(userEditor(u, request, tab));
    return page(request, title, body.toString());
  }

  @PostMapping("/admin/users")
  public RedirectView create(HttpServletRequest request, RedirectAttributes redirect) {
    String userName = request.getParameter("userName");
    String password = request.getParameter("password");
    String confirm = request.getParameter("confirmPassword");
    if (userName == null || userName.isBlank() || password == null || password.isBlank()) {
      AdminRequests.notice(redirect, "userName and password are required");
      return new RedirectView("/admin/users/new?tab=password", true);
    }
    if (confirm == null || !password.equals(confirm)) {
      AdminRequests.notice(redirect, "Password and confirm password do not match");
      return new RedirectView("/admin/users/new?tab=password", true);
    }
    String id =
        repos()
            .users()
            .insert(
                userName,
                request.getParameter("email"),
                AdminRequests.checked(request, "emailConfirmed"),
                hasher.hash(password));
    repos().users().update(id, readProfile(request));
    AdminRequests.notice(redirect, "Created user");
    return new RedirectView("/admin/users/" + id + "?tab=profile", true);
  }

  @PostMapping("/admin/users/{id}")
  public RedirectView update(
      @PathVariable String id, HttpServletRequest request, RedirectAttributes redirect) {
    repos().users().update(id, readProfile(request));
    AdminRequests.notice(redirect, "Saved");
    return new RedirectView("/admin/users/" + id + "?tab=profile", true);
  }

  @PostMapping("/admin/users/{id}/password")
  public RedirectView password(
      @PathVariable String id, HttpServletRequest request, RedirectAttributes redirect) {
    String password = request.getParameter("password");
    String confirm = request.getParameter("confirmPassword");
    if (password == null || password.isBlank()) {
      AdminRequests.notice(redirect, "Password is required");
    } else if (confirm == null || !password.equals(confirm)) {
      AdminRequests.notice(redirect, "Password and confirm password do not match");
    } else {
      repos().users().setPasswordHash(id, hasher.hash(password));
      AdminRequests.notice(redirect, "Password updated (Identity v3)");
    }
    return new RedirectView("/admin/users/" + id + "?tab=password", true);
  }

  @PostMapping("/admin/users/{id}/roles")
  public RedirectView addRole(
      @PathVariable String id, HttpServletRequest request, RedirectAttributes redirect) {
    String lookup = request.getParameter("roleName");
    if (lookup == null || lookup.isBlank()) {
      lookup = request.getParameter("roleId");
    }
    String roleKey = lookup == null ? "" : lookup;
    var role =
        repos()
            .roles()
            .findByNormalizedName(roleKey)
            .or(() -> repos().roles().findById(roleKey));
    if (role.isEmpty()) {
      AdminRequests.notice(redirect, "Role not found");
    } else if (repos().users().listRoleNames(id).contains(role.get().name())) {
      AdminRequests.notice(redirect, "Role already assigned");
    } else {
      repos().users().addRole(id, role.get().id());
      AdminRequests.notice(redirect, "Role assigned");
    }
    return new RedirectView("/admin/users/" + id + "?tab=roles", true);
  }

  @PostMapping("/admin/users/{id}/roles/{roleId}/delete")
  public RedirectView removeRole(
      @PathVariable String id, @PathVariable String roleId, RedirectAttributes redirect) {
    repos().users().removeRole(id, roleId);
    AdminRequests.notice(redirect, "Role removed");
    return new RedirectView("/admin/users/" + id + "?tab=roles", true);
  }

  @PostMapping("/admin/users/{id}/claims")
  public RedirectView addClaim(
      @PathVariable String id, HttpServletRequest request, RedirectAttributes redirect) {
    repos().users().addClaim(id, request.getParameter("type"), request.getParameter("value"));
    AdminRequests.notice(redirect, "Claim added");
    return new RedirectView("/admin/users/" + id + "?tab=claims", true);
  }

  @PostMapping("/admin/users/{id}/claims/{claimId}/delete")
  public RedirectView deleteClaim(
      @PathVariable String id, @PathVariable int claimId, RedirectAttributes redirect) {
    repos().users().deleteClaim(id, claimId);
    AdminRequests.notice(redirect, "Claim deleted");
    return new RedirectView("/admin/users/" + id + "?tab=claims", true);
  }

  @PostMapping("/admin/users/{id}/providers/delete")
  public RedirectView deleteProvider(
      @PathVariable String id, HttpServletRequest request, RedirectAttributes redirect) {
    int n =
        repos()
            .users()
            .deleteLogin(
                id, request.getParameter("loginProvider"), request.getParameter("providerKey"));
    AdminRequests.notice(redirect, n > 0 ? "Provider removed" : "Provider not found");
    return new RedirectView("/admin/users/" + id + "?tab=providers", true);
  }

  @PostMapping("/admin/users/{id}/delete")
  public RedirectView delete(@PathVariable String id, RedirectAttributes redirect) {
    repos().users().delete(id);
    AdminRequests.notice(redirect, "Deleted user");
    return new RedirectView("/admin/users", true);
  }

  private String userEditor(IdentityUser existing, HttpServletRequest request, String startTab) {
    boolean create = existing == null;
    String id = create ? "" : existing.id();
    String csrf =
        AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));
    String tab = normalizeTab(startTab, create);

    StringBuilder html = new StringBuilder("<div class=\"editor\">");
    html.append(
        "<p class=\"lead\">Identity user (ASP.NET Identity tables). Password hashes stay Identity v3.</p>");
    html.append("<div class=\"tabs\" data-tabs data-initial-tab=\"")
        .append(AdminHtml.esc(tab))
        .append("\">");
    html.append("<div class=\"tab-list\" role=\"tablist\" aria-label=\"User settings\">");
    html.append(tabButton("profile", "Profile", "profile".equals(tab)));
    if (!create) {
      html.append(tabButton("roles", "Roles", "roles".equals(tab)));
      html.append(tabButton("claims", "Claims", "claims".equals(tab)));
      html.append(tabButton("providers", "Providers", "providers".equals(tab)));
      html.append(tabButton("password", "Password", "password".equals(tab)));
    } else {
      html.append(tabButton("password", "Password", "password".equals(tab)));
    }
    html.append("</div>");

    // Profile
    html.append("<div class=\"tab-panel")
        .append("profile".equals(tab) ? " is-active" : "")
        .append("\" data-panel=\"profile\" role=\"tabpanel\">");
    html.append("<form method=\"post\" action=\"")
        .append(create ? "/admin/users" : "/admin/users/" + AdminHtml.esc(id))
        .append("\">")
        .append(csrf);
    if (create) {
      html.append("<input type=\"hidden\" name=\"password\" id=\"create-password-mirror\" value=\"\">");
      html.append(
          "<input type=\"hidden\" name=\"confirmPassword\" id=\"create-confirm-mirror\" value=\"\">");
    }
    html.append("<h2>").append(create ? "New user" : "User").append("</h2>");
    if (!create) {
      html.append("<p class=\"meta-line\"><span class=\"meta-label\">Subject (Id)</span> ");
      html.append("<code class=\"meta-value\">")
          .append(AdminHtml.esc(existing.id()))
          .append("</code>");
      html.append(" <span class=\"hint\">OIDC <code>sub</code></span></p>");
    }
    html.append("<div class=\"grid-2\">");
    html.append(textField("userName", "Username", create ? "" : nullToEmpty(existing.userName()), true));
    html.append(textField("email", "Email", create ? "" : nullToEmpty(existing.email()), false));
    html.append(
        textField(
            "phoneNumber",
            "Phone number",
            create ? "" : nullToEmpty(existing.phoneNumber()),
            false));
    if (!create) {
      html.append(
          textField(
              "accessFailedCount",
              "Access failed count",
              String.valueOf(existing.accessFailedCount()),
              false));
      html.append(
          "<div class=\"field\"><label>Lockout end</label><input name=\"lockoutEnd\" type=\"datetime-local\" value=\"")
          .append(AdminHtml.esc(formatLockout(existing.lockoutEnd())))
          .append("\"><span class=\"hint\">UTC. Clear to unlock immediately.</span></div>");
    }
    html.append("</div>");
    html.append("<div class=\"checks\" style=\"margin-top:.85rem\">");
    html.append(
        AdminHtml.flagCheckbox(
            "emailConfirmed",
            "Email confirmed",
            "Users.EmailConfirmed",
            !create && existing.emailConfirmed()));
    html.append(
        AdminHtml.flagCheckbox(
            "phoneNumberConfirmed",
            "Phone number confirmed",
            "Users.PhoneNumberConfirmed",
            !create && existing.phoneNumberConfirmed()));
    html.append(
        AdminHtml.flagCheckbox(
            "lockoutEnabled",
            "Lockout enabled",
            "Users.LockoutEnabled",
            !create && existing.lockoutEnabled()));
    html.append(
        AdminHtml.flagCheckbox(
            "twoFactorEnabled",
            "Two-factor enabled",
            "Users.TwoFactorEnabled",
            !create && existing.twoFactorEnabled()));
    html.append("</div>");
    if (create) {
      html.append(
          "<p class=\"hint\" style=\"margin:.85rem 0 0\">Set the initial password on the Password tab, then Create.</p>");
      html.append(
          "<div class=\"form-actions\"><button type=\"submit\" data-create-user>Create user</button></div>");
    } else {
      html.append("<div class=\"form-actions\"><button type=\"submit\">Save profile</button></div>");
    }
    html.append("</form></div>");

    // Password (aligned with C# UserChangePassword: username + password + confirm)
    html.append("<div class=\"tab-panel")
        .append("password".equals(tab) ? " is-active" : "")
        .append("\" data-panel=\"password\" role=\"tabpanel\">");
    html.append("<div class=\"password-card\">");
    if (create) {
      html.append("<h2>Initial password</h2>");
      html.append(
          "<p class=\"hint\" style=\"margin:0 0 .85rem\">Required to create. Stored as ASP.NET Identity v3 PBKDF2.</p>");
      html.append("<div class=\"field\"><label>Password</label>");
      html.append(AdminHtml.passwordInput("create-password", "createPassword", true, "new-password"));
      html.append("</div>");
      html.append("<div class=\"field\"><label>Confirm password</label>");
      html.append(
          AdminHtml.passwordInput(
              "create-confirm-password", "createConfirmPassword", true, "new-password"));
      html.append("</div>");
      html.append(
          "<div class=\"form-actions\"><button type=\"button\" data-goto-create>Continue to Profile</button></div>");
    } else {
      html.append("<h2>Change password</h2>");
      html.append("<form method=\"post\" action=\"/admin/users/")
          .append(AdminHtml.esc(id))
          .append("/password\" data-password-form>")
          .append(csrf);
      html.append("<div class=\"field\"><label>Username</label>");
      html.append("<input type=\"text\" value=\"")
          .append(AdminHtml.esc(nullToEmpty(existing.userName())))
          .append("\" readonly>");
      html.append("</div>");
      html.append("<div class=\"field\"><label>Password</label>");
      html.append(AdminHtml.passwordInput("user-password", "password", true, "new-password"));
      html.append("</div>");
      html.append("<div class=\"field\"><label>Confirm password</label>");
      html.append(
          AdminHtml.passwordInput("user-confirm-password", "confirmPassword", true, "new-password"));
      html.append("</div>");
      html.append(
          "<p class=\"hint\" style=\"margin:.65rem 0 0\">Replaces PasswordHash with Identity v3; SecurityStamp rotates.</p>");
      html.append(
          "<div class=\"form-actions\"><button type=\"submit\">Change password</button></div>");
      html.append("</form>");
    }
    html.append("</div></div>");

    if (!create) {
      html.append(rolesPanel(existing, csrf, "roles".equals(tab)));
      html.append(claimsPanel(existing, csrf, "claims".equals(tab)));
      html.append(providersPanel(existing, csrf, "providers".equals(tab)));
    }

    html.append("</div></div>"); // tabs, editor
    html.append(AdminHtml.clientEditorScript());
    html.append(createUserScript());
    return html.toString();
  }

  private String rolesPanel(IdentityUser user, String csrf, boolean active) {
    String id = user.id();
    StringBuilder html = new StringBuilder("<div class=\"tab-panel");
    html.append(active ? " is-active" : "")
        .append("\" data-panel=\"roles\" role=\"tabpanel\">");
    html.append("<h2>Roles</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">Identity <code>UserRoles</code>. Admin access requires <code>idserver.admin.role</code>.</p>");
    List<IdentityRole> roles = repos().users().listRoles(id);
    if (roles.isEmpty()) {
      html.append("<p class=\"muted\">No roles assigned.</p>");
    } else {
      html.append("<table><tr><th>Name</th><th></th></tr>");
      for (IdentityRole role : roles) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(role.name()))
            .append("</td><td><form method=\"post\" action=\"/admin/users/")
            .append(AdminHtml.esc(id))
            .append("/roles/")
            .append(AdminHtml.esc(role.id()))
            .append("/delete\" style=\"display:inline\">")
            .append(csrf)
            .append("<button type=\"submit\" class=\"ghost\">Remove</button></form></td></tr>");
      }
      html.append("</table>");
    }
    html.append("<form method=\"post\" action=\"/admin/users/")
        .append(AdminHtml.esc(id))
        .append("/roles\">")
        .append(csrf);
    html.append("<div class=\"field\"><label>Add role</label><select name=\"roleName\">");
    List<String> assigned = repos().users().listRoleNames(id);
    int options = 0;
    for (String name : repos().roles().listNames(200)) {
      if (assigned.contains(name)) {
        continue;
      }
      options++;
      html.append("<option value=\"")
          .append(AdminHtml.esc(name))
          .append("\">")
          .append(AdminHtml.esc(name))
          .append("</option>");
    }
    html.append("</select></div>");
    if (options == 0) {
      html.append("<p class=\"hint\">All roles are already assigned, or Roles is empty.</p>");
    } else {
      html.append("<div class=\"form-actions\"><button type=\"submit\">Add role</button></div>");
    }
    html.append("</form></div>");
    return html.toString();
  }

  private String claimsPanel(IdentityUser user, String csrf, boolean active) {
    String id = user.id();
    StringBuilder html = new StringBuilder("<div class=\"tab-panel");
    html.append(active ? " is-active" : "")
        .append("\" data-panel=\"claims\" role=\"tabpanel\">");
    html.append("<h2>User claims</h2>");
    html.append(
        "<p class=\"hint\" style=\"margin:0 0 .75rem\">Identity <code>UserClaims</code>. Pick a common OpenID type or enter a custom claim type.</p>");
    // C# UserClaims: add form first (type suggestions + value), then existing table.
    html.append("<form method=\"post\" action=\"/admin/users/")
        .append(AdminHtml.esc(id))
        .append("/claims\">")
        .append(csrf);
    html.append(
        AdminHtml.claimTypeValueRow("user-claim-type", "type", "user-claim-value", "value"));
    html.append("<div class=\"form-actions\"><button type=\"submit\">Add user claim</button></div>");
    html.append("</form>");
    html.append("<h2 style=\"margin-top:1.35rem\">User claims</h2>");
    List<UserClaim> claims = repos().users().listClaims(id);
    if (claims.isEmpty()) {
      html.append("<p class=\"muted\">No claims yet.</p>");
    } else {
      html.append("<table><tr><th>Type</th><th>Value</th><th></th></tr>");
      for (UserClaim claim : claims) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(claim.type()))
            .append("</td><td>")
            .append(AdminHtml.esc(claim.value()))
            .append("</td><td><form method=\"post\" action=\"/admin/users/")
            .append(AdminHtml.esc(id))
            .append("/claims/")
            .append(claim.id())
            .append("/delete\" style=\"display:inline\"")
            .append(AdminHtml.dataConfirm("Delete this claim?"))
            .append(">")
            .append(csrf)
            .append("<button type=\"submit\" class=\"ghost\">Delete</button></form></td></tr>");
      }
      html.append("</table>");
    }
    html.append(AdminHtml.claimTypePickerScript());
    html.append("</div>");
    return html.toString();
  }

  private String providersPanel(IdentityUser user, String csrf, boolean active) {
    String id = user.id();
    StringBuilder html = new StringBuilder("<div class=\"tab-panel");
    html.append(active ? " is-active" : "")
        .append("\" data-panel=\"providers\" role=\"tabpanel\">");
    html.append("<h2>External login providers</h2>");
    html.append("<div class=\"callout warn\" style=\"margin:0 0 1rem\">");
    html.append(
        "<strong>Read / unlink only ??same as C# Admin User Providers.</strong><br>");
    html.append(
        "This lists rows in Identity <code>UserLogins</code>: accounts the user already linked "
            + "(Google, Azure AD, Facebook, ?? when they signed in on <strong>STS</strong> via an "
            + "external provider.<br>");
    html.append(
        "You cannot ?�add a provider??here. Linking happens on STS after external login; use "
            + "<strong>Remove</strong> only to force the user to re-link or stop using that provider.");
    html.append("</div>");
    List<UserLogin> logins = repos().users().listLogins(id);
    if (logins.isEmpty()) {
      html.append("<p class=\"muted\">No external logins linked for this user.</p>");
      html.append(
          "<p class=\"hint\">Empty is normal for password-only users (e.g. <code>demo</code>). "
              + "When STS has external providers enabled and the user links one, it appears here.</p>");
    } else {
      html.append(
          "<table><tr><th>Provider</th><th>Display name</th><th>Provider key</th><th></th></tr>");
      for (UserLogin login : logins) {
        html.append("<tr><td>")
            .append(AdminHtml.esc(login.loginProvider()))
            .append("</td><td>")
            .append(AdminHtml.esc(login.providerDisplayName() == null ? "" : login.providerDisplayName()))
            .append("</td><td><code>")
            .append(AdminHtml.esc(login.providerKey()))
            .append("</code></td><td><form method=\"post\" action=\"/admin/users/")
            .append(AdminHtml.esc(id))
            .append("/providers/delete\" style=\"display:inline\"")
            .append(
                AdminHtml.dataConfirm(
                    "Remove this external login? The user must link it again to use it.",
                    "Remove provider",
                    "Remove",
                    true))
            .append(">")
            .append(csrf)
            .append("<input type=\"hidden\" name=\"loginProvider\" value=\"")
            .append(AdminHtml.esc(login.loginProvider()))
            .append("\"><input type=\"hidden\" name=\"providerKey\" value=\"")
            .append(AdminHtml.esc(login.providerKey()))
            .append("\"><button type=\"submit\" class=\"ghost\">Remove</button></form></td></tr>");
      }
      html.append("</table>");
    }
    html.append("</div>");
    return html.toString();
  }

  private UserProfileWrite readProfile(HttpServletRequest request) {
    int failed = 0;
    String failedRaw = request.getParameter("accessFailedCount");
    if (failedRaw != null && !failedRaw.isBlank()) {
      try {
        failed = Integer.parseInt(failedRaw.trim());
      } catch (NumberFormatException ignored) {
        failed = 0;
      }
    }
    return new UserProfileWrite(
        request.getParameter("userName"),
        request.getParameter("email"),
        AdminRequests.checked(request, "emailConfirmed"),
        request.getParameter("phoneNumber"),
        AdminRequests.checked(request, "phoneNumberConfirmed"),
        AdminRequests.checked(request, "lockoutEnabled"),
        parseLockoutEnd(request.getParameter("lockoutEnd")),
        failed,
        AdminRequests.checked(request, "twoFactorEnabled"));
  }

  private static Instant parseLockoutEnd(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDateTime.parse(raw.trim(), LOCKOUT_LOCAL).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException e) {
      try {
        return Instant.parse(raw.trim());
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
  }

  private static String formatLockout(Instant lockoutEnd) {
    if (lockoutEnd == null) {
      return "";
    }
    return LOCKOUT_LOCAL.format(LocalDateTime.ofInstant(lockoutEnd, ZoneOffset.UTC));
  }

  private static String textField(String name, String label, String value, boolean required) {
    return "<div class=\"field\"><label>"
        + AdminHtml.esc(label)
        + "</label><input name=\""
        + AdminHtml.esc(name)
        + "\" value=\""
        + AdminHtml.esc(value)
        + "\""
        + (required ? " required" : "")
        + " autocomplete=\"off\"></div>";
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

  private static String tabParam(HttpServletRequest request, String fallback) {
    String tab = request.getParameter("tab");
    return tab == null || tab.isBlank() ? fallback : tab.trim();
  }

  private static String normalizeTab(String tab, boolean create) {
    if (create) {
      return "password".equals(tab) ? "password" : "profile";
    }
    return switch (tab == null ? "" : tab) {
      case "roles", "claims", "providers", "password" -> tab;
      default -> "profile";
    };
  }

  private static String createUserScript() {
    return """
        <script>
        (function(){
          var pwd=document.getElementById('create-password');
          var confirm=document.getElementById('create-confirm-password');
          var mirror=document.getElementById('create-password-mirror');
          var confirmMirror=document.getElementById('create-confirm-mirror');
          var goto=document.querySelector('[data-goto-create]');
          var createBtn=document.querySelector('[data-create-user]');
          function sync(){
            if(pwd&&mirror) mirror.value=pwd.value||'';
            if(confirm&&confirmMirror) confirmMirror.value=confirm.value||'';
          }
          function passwordsReady(){
            sync();
            if(!pwd||!pwd.value){ pwd&&pwd.focus(); return false; }
            if(!confirm||!confirm.value){ confirm&&confirm.focus(); return false; }
            if(pwd.value!==confirm.value){
              if(window.SkorubaMsg){ SkorubaMsg.alert('Password and confirm password do not match',{title:'Password'}); }
              confirm.focus();
              return false;
            }
            return true;
          }
          function goPasswordTab(){
            document.querySelectorAll('[data-tabs]').forEach(function(root){
              if(typeof root._activateTab==='function') root._activateTab('password');
            });
          }
          if(pwd){ pwd.addEventListener('input',sync); }
          if(confirm){ confirm.addEventListener('input',sync); }
          sync();
          if(goto){
            goto.addEventListener('click',function(){
              if(!passwordsReady()) return;
              document.querySelectorAll('[data-tabs]').forEach(function(root){
                if(typeof root._activateTab==='function') root._activateTab('profile');
              });
            });
          }
          if(createBtn){
            createBtn.addEventListener('click',function(e){
              if(!passwordsReady()){
                e.preventDefault();
                goPasswordTab();
              }
            });
          }
          document.querySelectorAll('form[data-password-form]').forEach(function(form){
            form.addEventListener('submit',function(e){
              var a=form.querySelector('#user-password');
              var b=form.querySelector('#user-confirm-password');
              if(a&&b&&a.value!==b.value){
                e.preventDefault();
                if(window.SkorubaMsg){ SkorubaMsg.alert('Password and confirm password do not match',{title:'Password'}); }
                else{ b.focus(); }
              }
            });
          });
        })();
        </script>
        """;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
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
