package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.AuditLogEntry;
import com.myano.skoruba4j.domain.configstore.AuditLogFilter;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

/** Skoruba Admin Audit Log browser (read + delete-older-than). */
@Controller
public class AuditLogsAdminController {
  private static final DateTimeFormatter DISPLAY_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private final Optional<JdbcRepositories> jdbc;

  public AuditLogsAdminController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/admin/audit-logs", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false)
          String subjectIdentifier,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String subjectName,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String event,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String source,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String category,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    AuditLogFilter filter =
        AuditLogFilter.of(subjectIdentifier, subjectName, event, source, category, page, 20);
    String csrf =
        AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));
    StringBuilder body =
        new StringBuilder("<h1>").append(AdminHtml.esc(Messages.t("audit.title"))).append("</h1>");
    body.append(
        "<p class=\"lead\">Skoruba <code>AuditLog</code> ??Admin configuration / identity actions. "
            + "Same filters as C# Audit Log.</p>");

    if (!repos().auditLogs().tableReady()) {
      body.append(
          "<div class=\"callout warn\">Table <code>AuditLog</code> is missing on this database. "
              + "SQLite demo schema creates it automatically; SQL Server needs the Skoruba AuditLogging "
              + "migration (or create the table manually).</div>");
      return page(request, Messages.t("audit.title"), body.toString());
    }

    body.append("<div class=\"page-head\" style=\"align-items:flex-end\">");
    body.append("<div></div>");
    body.append("<form method=\"post\" action=\"/admin/audit-logs/delete-older\" class=\"page-actions\"")
        .append(
            AdminHtml.dataConfirm(
                "Delete audit logs older than the selected date? This cannot be undone.",
                "Delete old logs",
                "Delete",
                true))
        .append(">")
        .append(csrf);
    body.append("<label style=\"font-size:.85rem;color:var(--muted)\">Delete logs older than ");
    body.append("<input type=\"date\" name=\"olderThan\" required value=\"")
        .append(LocalDate.now(ZoneOffset.UTC).minusMonths(1))
        .append("\"></label> ");
    body.append("<button type=\"submit\" class=\"danger\">Delete</button>");
    body.append("</form></div>");

    body.append("<form method=\"get\" action=\"/admin/audit-logs\" class=\"filter-bar\">");
    body.append("<div class=\"grid-5\">");
    body.append(filterField("subjectIdentifier", "Subject Identifier", filter.subjectIdentifier()));
    body.append(filterField("subjectName", "Subject Name", filter.subjectName()));
    body.append(filterField("event", "Event", filter.event()));
    body.append(filterField("source", "Source", filter.source()));
    body.append(filterField("category", "Category", filter.category()));
    body.append("</div>");
    body.append("<p style=\"margin:.75rem 0 0\"><button type=\"submit\">Search</button> ");
    body.append("<a href=\"/admin/audit-logs\">Clear</a></p>");
    body.append("</form>");

    try {
      PageResult<AuditLogEntry> result = repos().auditLogs().search(filter);
      body.append(
          "<table><tr><th></th><th>Event</th><th>Source</th><th>Subject</th><th>Action</th><th>Created</th></tr>");
      for (AuditLogEntry row : result.items()) {
        body.append("<tr><td>");
        body.append("<button type=\"button\" data-open-dialog=\"audit-")
            .append(row.id())
            .append("\">Detail</button>");
        body.append("</td><td>")
            .append(AdminHtml.esc(nullToDash(row.event())))
            .append("</td><td>")
            .append(AdminHtml.esc(nullToDash(row.source())))
            .append("</td><td>");
        body.append("<button type=\"button\" class=\"linkish\" data-open-dialog=\"audit-")
            .append(row.id())
            .append("\">")
            .append(AdminHtml.esc(subjectLabel(row)))
            .append("</button>");
        body.append("</td><td>");
        body.append("<button type=\"button\" class=\"linkish\" data-open-dialog=\"audit-")
            .append(row.id())
            .append("\">Show detail</button>");
        body.append("</td><td>")
            .append(AdminHtml.esc(formatInstant(row.created())))
            .append("</td></tr>");
        body.append(detailDialog(row));
      }
      body.append("</table>");
      body.append(filterPager("/admin/audit-logs", filter, result.page(), result.pageSize(), result.totalCount()));
    } catch (UncheckedSqlException e) {
      body.append("<div class=\"callout danger\">")
          .append(AdminHtml.esc(e.getMessage()))
          .append("</div>");
    }

    body.append(dialogScript());
    return page(request, Messages.t("audit.title"), body.toString());
  }

  @GetMapping(value = "/admin/audit-logs/{id}", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String detail(@PathVariable long id, HttpServletRequest request) {
    AuditLogEntry row =
        repos()
            .auditLogs()
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    StringBuilder body = new StringBuilder("<h1>Audit log #").append(row.id()).append("</h1>");
    body.append("<p><a href=\"/admin/audit-logs\">Back to Audit Log</a></p>");
    body.append(detailBody(row));
    return page(request, "Audit #" + row.id(), body.toString());
  }

  @PostMapping("/admin/audit-logs/delete-older")
  public RedirectView deleteOlder(HttpServletRequest request, RedirectAttributes redirect) {
    Instant cutoff = parseDateStart(request.getParameter("olderThan"));
    if (cutoff == null) {
      AdminRequests.notice(redirect, "Pick a valid date");
      return new RedirectView("/admin/audit-logs", true);
    }
    try {
      int n = repos().auditLogs().deleteOlderThan(cutoff);
      AdminRequests.notice(redirect, "Deleted " + n + " audit log(s) older than " + cutoff);
    } catch (UncheckedSqlException | IllegalArgumentException e) {
      AdminRequests.notice(redirect, e.getMessage());
    }
    return new RedirectView("/admin/audit-logs", true);
  }

  private static String filterField(String name, String label, String value) {
    return "<div class=\"field\"><label>"
        + AdminHtml.esc(label)
        + "</label><input name=\""
        + AdminHtml.esc(name)
        + "\" value=\""
        + AdminHtml.esc(value == null ? "" : value)
        + "\" autocomplete=\"off\"></div>";
  }

  private static String detailDialog(AuditLogEntry row) {
    StringBuilder html = new StringBuilder();
    html.append("<dialog class=\"form-dialog\" id=\"audit-")
        .append(row.id())
        .append("\" aria-labelledby=\"audit-title-")
        .append(row.id())
        .append("\">");
    html.append("<div class=\"sheet\">");
    html.append("<h2 id=\"audit-title-")
        .append(row.id())
        .append("\">")
        .append(AdminHtml.esc(nullToDash(row.event())))
        .append("</h2>");
    html.append(detailBody(row));
    html.append("<div class=\"actions\">");
    html.append("<button type=\"button\" class=\"ghost\" data-close-dialog>Close</button>");
    html.append("</div></div></dialog>");
    return html.toString();
  }

  private static String detailBody(AuditLogEntry row) {
    StringBuilder html = new StringBuilder("<dl class=\"audit-dl\">");
    row("Event", row.event(), html);
    row("Source", row.source(), html);
    row("Category", row.category(), html);
    row("Subject Identifier", row.subjectIdentifier(), html);
    row("Subject Name", row.subjectName(), html);
    row("Subject Type", row.subjectType(), html);
    row("Subject Additional Data", row.subjectAdditionalData(), html);
    row("Action", row.action(), html);
    row("Created", formatInstant(row.created()), html);
    html.append("<dt>Data</dt><dd><pre class=\"audit-data\">")
        .append(AdminHtml.esc(row.data() == null || row.data().isBlank() ? "—" : row.data()))
        .append("</pre></dd>");
    html.append("</dl>");
    return html.toString();
  }

  private static void row(String label, String value, StringBuilder html) {
    html.append("<dt>")
        .append(AdminHtml.esc(label))
        .append("</dt><dd>")
        .append(AdminHtml.esc(nullToDash(value)))
        .append("</dd>");
  }

  private static String subjectLabel(AuditLogEntry row) {
    if (row.subjectName() != null && !row.subjectName().isBlank()) {
      return row.subjectName() + " ??Show detail";
    }
    if (row.subjectIdentifier() != null && !row.subjectIdentifier().isBlank()) {
      return row.subjectIdentifier() + " ??Show detail";
    }
    return "Show detail";
  }

  private static String filterPager(
      String path, AuditLogFilter filter, int page, int pageSize, int total) {
    int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
    String q = queryString(filter);
    StringBuilder html = new StringBuilder("<p class=\"pager\">");
    if (page > 1) {
      html.append("<a href=\"")
          .append(AdminHtml.esc(path))
          .append("?")
          .append(q)
          .append("page=")
          .append(page - 1)
          .append("\">prev</a> ");
    }
    html.append("page ").append(page).append(" / ").append(pages).append(" (").append(total).append(")");
    if (page < pages) {
      html.append(" <a href=\"")
          .append(AdminHtml.esc(path))
          .append("?")
          .append(q)
          .append("page=")
          .append(page + 1)
          .append("\">next</a>");
    }
    html.append("</p>");
    return html.toString();
  }

  private static String queryString(AuditLogFilter filter) {
    StringBuilder q = new StringBuilder();
    appendParam(q, "subjectIdentifier", filter.subjectIdentifier());
    appendParam(q, "subjectName", filter.subjectName());
    appendParam(q, "event", filter.event());
    appendParam(q, "source", filter.source());
    appendParam(q, "category", filter.category());
    return q.toString();
  }

  private static void appendParam(StringBuilder q, String name, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    q.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
        .append('=')
        .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
        .append('&');
  }

  private static Instant parseDateStart(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim()).atStartOfDay(ZoneOffset.UTC).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String formatInstant(Instant value) {
    if (value == null) {
      return "—";
    }
    return DISPLAY_UTC.format(value) + " UTC";
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private static String dialogScript() {
    return """
        <script>
        (function(){
          function openDialog(id){
            var d=document.getElementById(id);
            if(d&&typeof d.showModal==='function') d.showModal();
          }
          document.querySelectorAll('[data-open-dialog]').forEach(function(btn){
            btn.addEventListener('click',function(){
              openDialog(btn.getAttribute('data-open-dialog'));
            });
          });
          document.querySelectorAll('dialog.form-dialog').forEach(function(dlg){
            dlg.addEventListener('click',function(ev){ if(ev.target===dlg) dlg.close(); });
            dlg.querySelectorAll('[data-close-dialog]').forEach(function(btn){
              btn.addEventListener('click',function(){ dlg.close(); });
            });
          });
        })();
        </script>
        """;
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
