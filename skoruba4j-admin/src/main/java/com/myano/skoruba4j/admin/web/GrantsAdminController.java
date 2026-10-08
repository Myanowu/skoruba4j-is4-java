package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.PersistedGrantRecord;
import com.myano.skoruba4j.domain.configstore.PersistedGrantSubject;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * Skoruba Persisted Grants: subject list ??per-subject grant detail (same shape as C# Grant /
 * PersistedGrants).
 */
@Controller
public class GrantsAdminController {
  private static final DateTimeFormatter DISPLAY_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private final Optional<JdbcRepositories> jdbc;

  public GrantsAdminController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/admin/grants", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String searchText,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    PageQuery query = PageQuery.of(searchText, page, 20);
    StringBuilder body =
        new StringBuilder("<h1>").append(AdminHtml.esc(Messages.t("grants.title"))).append("</h1>");
    body.append(
        "<p class=\"lead\">IS4 <code>PersistedGrants</code> grouped by subject (refresh tokens, "
            + "authorization codes, reference tokens). Open Detail to revoke one grant or all for a user.</p>");
    body.append(AdminHtml.searchBar("/admin/grants", query.searchText(), query.page()));
    PageResult<PersistedGrantSubject> result = repos().persistedGrants().searchSubjects(query);
    body.append("<table><tr><th></th><th>Subject Identifier</th><th>Subject Name</th></tr>");
    if (result.items().isEmpty()) {
      body.append(
          "<tr><td colspan=\"3\" class=\"hint\">No persisted grants yet. They appear after STS "
              + "issues refresh tokens / codes into this database.</td></tr>");
    }
    for (PersistedGrantSubject row : result.items()) {
      String sid = row.subjectId() == null ? "" : row.subjectId();
      body.append("<tr><td><a class=\"button\" href=\"/admin/grants/subjects/")
          .append(enc(sid))
          .append("\">Detail</a></td><td><code>")
          .append(AdminHtml.esc(sid))
          .append("</code></td><td>")
          .append(AdminHtml.esc(blank(row.subjectName())))
          .append("</td></tr>");
    }
    body.append("</table>");
    body.append(
        AdminHtml.pager(
            "/admin/grants", query.searchText(), result.page(), result.pageSize(), result.totalCount()));
    return page(request, Messages.t("grants.title"), body.toString());
  }

  @GetMapping(value = "/admin/grants/subjects/{subjectId}", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String subjectDetail(
      HttpServletRequest request,
      @PathVariable String subjectId,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    PageQuery query = PageQuery.of(null, page, 20);
    String csrf =
        AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));
    String name =
        repos()
            .persistedGrants()
            .resolveSubjectName(subjectId)
            .filter(s -> !s.isBlank())
            .orElse("");
    PageResult<PersistedGrantRecord> result =
        repos().persistedGrants().searchBySubject(subjectId, query);

    StringBuilder body = new StringBuilder();
    body.append("<p class=\"crumbs\"><a href=\"/admin/grants\">")
        .append(AdminHtml.esc(Messages.t("grants.title")))
        .append("</a> / ");
    body.append(AdminHtml.esc(subjectId)).append("</p>");
    body.append("<div class=\"page-head\"><h1>")
        .append(AdminHtml.esc(Messages.t("grants.title")))
        .append("</h1>");
    body.append("<form method=\"post\" action=\"/admin/grants/subjects/")
        .append(enc(subjectId))
        .append("/delete\"")
        .append(
            AdminHtml.dataConfirm(
                "Delete ALL persisted grants for this subject?",
                "Delete all grants",
                "Delete all",
                true))
        .append(">")
        .append(csrf)
        .append("<button type=\"submit\" class=\"danger\">Delete all</button></form>");
    body.append("</div>");
    body.append("<p class=\"meta-line\"><span>Subject Id</span><code>")
        .append(AdminHtml.esc(subjectId))
        .append("</code>");
    if (!name.isBlank()) {
      body.append("<span>Subject Name</span><strong>")
          .append(AdminHtml.esc(name))
          .append("</strong>");
    }
    body.append("</p>");

    body.append(
        "<table><tr><th></th><th>Type</th><th>Client</th><th>Session</th><th>Description</th>"
            + "<th>Created</th><th>Expiration</th><th>Consumed</th><th>Data</th></tr>");
    if (result.items().isEmpty()) {
      body.append("<tr><td colspan=\"9\" class=\"hint\">No grants for this subject.</td></tr>");
    }
    for (PersistedGrantRecord g : result.items()) {
      body.append("<tr><td>");
      body.append("<form method=\"post\" action=\"/admin/grants/revoke\"")
          .append(AdminHtml.dataConfirm("Revoke this grant?"))
          .append(">")
          .append(csrf)
          .append("<input type=\"hidden\" name=\"key\" value=\"")
          .append(AdminHtml.esc(g.key()))
          .append("\">")
          .append("<input type=\"hidden\" name=\"returnSubject\" value=\"")
          .append(AdminHtml.esc(subjectId))
          .append("\">")
          .append("<button type=\"submit\">Revoke</button></form>");
      body.append("</td><td>")
          .append(AdminHtml.esc(blank(g.type())))
          .append("</td><td>")
          .append(AdminHtml.esc(blank(g.clientId())))
          .append("</td><td><code>")
          .append(AdminHtml.esc(blank(g.sessionId())))
          .append("</code></td><td>")
          .append(AdminHtml.esc(blank(g.description())))
          .append("</td><td>")
          .append(AdminHtml.esc(formatInstant(g.creationTime())))
          .append("</td><td>")
          .append(AdminHtml.esc(formatInstant(g.expiration())))
          .append("</td><td>")
          .append(AdminHtml.esc(formatInstant(g.consumedTime())))
          .append("</td><td><code class=\"grant-data\">")
          .append(AdminHtml.esc(truncate(g.data(), 80)))
          .append("</code></td></tr>");
    }
    body.append("</table>");
    body.append(
        AdminHtml.pager(
            "/admin/grants/subjects/" + enc(subjectId),
            "",
            result.page(),
            result.pageSize(),
            result.totalCount()));
    return page(request, Messages.t("grants.title"), body.toString());
  }

  @PostMapping("/admin/grants/revoke")
  public RedirectView revoke(HttpServletRequest request, RedirectAttributes redirect) {
    String key = request.getParameter("key");
    if (key == null || key.isBlank()) {
      AdminRequests.notice(redirect, "Grant key is required");
      return new RedirectView("/admin/grants", true);
    }
    String trimmed = key.trim();
    repos()
        .persistedGrants()
        .findByKey(trimmed)
        .ifPresentOrElse(
            row -> {
              // Index rows store authorization id in Data; remove container + sibling indexes.
              if (row.data() != null
                  && !row.data().isBlank()
                  && !"authorization".equals(row.type())) {
                repos().persistedGrants().deleteAuthorizationBundle(row.data());
              } else {
                repos().persistedGrants().deleteAuthorizationBundle(trimmed);
              }
            },
            () -> repos().persistedGrants().deleteByKey(trimmed));
    AdminRequests.notice(redirect, "Grant revoked");
    String subject = request.getParameter("returnSubject");
    if (subject != null && !subject.isBlank()) {
      return new RedirectView("/admin/grants/subjects/" + enc(subject), true);
    }
    return new RedirectView("/admin/grants", true);
  }

  @PostMapping("/admin/grants/subjects/{subjectId}/delete")
  public RedirectView deleteSubject(@PathVariable String subjectId, RedirectAttributes redirect) {
    int n = repos().persistedGrants().deleteBySubject(subjectId);
    AdminRequests.notice(redirect, "Deleted " + n + " grant(s) for subject");
    return new RedirectView("/admin/grants", true);
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private static String truncate(String value, int max) {
    if (value == null || value.isBlank()) {
      return "—";
    }
    String t = value.trim();
    return t.length() <= max ? t : t.substring(0, max) + "—";
  }

  private static String formatInstant(Instant value) {
    if (value == null) {
      return "—";
    }
    return DISPLAY_UTC.format(value) + " UTC";
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
