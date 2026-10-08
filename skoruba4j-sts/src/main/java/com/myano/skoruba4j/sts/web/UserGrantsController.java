package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.configstore.PersistedGrantRecord;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class UserGrantsController {
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

  private final Optional<JdbcRepositories> jdbc;

  public UserGrantsController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = {"/grants", "/Grants"}, produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(Authentication authentication) {
    String userId = authentication == null ? "" : authentication.getName();
    List<PersistedGrantRecord> grants =
        jdbc.map(repos -> repos.persistedGrants().listBySubject(userId)).orElse(List.of());
    StringBuilder inner = new StringBuilder();
    inner.append("<p class=\"muted\">Stored authorization codes and refresh tokens for this user ");
    inner.append("(IS4 <code>PersistedGrants</code>).</p>");
    if (grants.isEmpty()) {
      inner.append("<p>No persisted grants for this account.</p>");
    } else {
      inner.append("<table><thead><tr><th>Client</th><th>Type</th><th>Created (UTC)</th><th>Expires (UTC)</th><th></th></tr></thead><tbody>");
      for (PersistedGrantRecord grant : grants) {
        inner.append("<tr><td>").append(StsPages.esc(grant.clientId())).append("</td>");
        inner.append("<td>").append(StsPages.esc(grant.type())).append("</td>");
        inner.append("<td>").append(when(grant.creationTime())).append("</td>");
        inner.append("<td>").append(when(grant.expiration())).append("</td>");
        inner.append("<td><form method=\"post\" action=\"/grants/revoke\">");
        inner.append(StsPages.hidden("key", grant.key() == null ? "" : grant.key()));
        inner.append("<button type=\"submit\">Revoke</button></form></td></tr>");
      }
      inner.append("</tbody></table>");
      inner.append("<div class=\"actions\"><form method=\"post\" action=\"/grants/revoke-all\">");
      inner.append("<button type=\"submit\">Revoke all</button></form></div>");
    }
    inner.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/\">Back to home</a></p>");
    return StsPages.document("Grants", "app-body", StsPages.app("Grants", inner.toString()));
  }

  @GetMapping(value = "/grants.json", produces = "application/json")
  @ResponseBody
  public List<Map<String, String>> listJson(Authentication authentication) {
    String userId = authentication == null ? "" : authentication.getName();
    return jdbc.map(repos -> repos.persistedGrants().listBySubject(userId)).orElse(List.of()).stream()
        .map(
            g ->
                Map.of(
                    "key", nullToEmpty(g.key()),
                    "type", nullToEmpty(g.type()),
                    "clientId", nullToEmpty(g.clientId()),
                    "created", when(g.creationTime()),
                    "expires", when(g.expiration())))
        .toList();
  }

  @PostMapping({"/grants/revoke", "/Grants/revoke"})
  public Object revoke(
      Authentication authentication,
      @RequestParam("key") String key,
      HttpServletRequest request) {
    String userId = authentication == null ? "" : authentication.getName();
    jdbc.ifPresent(
        repos -> {
          List<PersistedGrantRecord> mine = repos.persistedGrants().listBySubject(userId);
          boolean owned =
              mine.stream().anyMatch(g -> g.key() != null && g.key().equals(key));
          if (owned) {
            PersistedGrantRecord row =
                mine.stream().filter(g -> key.equals(g.key())).findFirst().orElse(null);
            if (row != null
                && row.data() != null
                && !row.data().isBlank()
                && !"authorization".equals(row.type())) {
              repos.persistedGrants().deleteAuthorizationBundle(row.data());
            } else {
              repos.persistedGrants().deleteByKey(key);
            }
          }
        });
    if (dialog(request)) {
      return ResponseEntity.noContent().build();
    }
    return new RedirectView("/grants", true);
  }

  @PostMapping({"/grants/revoke-all", "/Grants/revoke-all"})
  public Object revokeAll(Authentication authentication, HttpServletRequest request) {
    String userId = authentication == null ? "" : authentication.getName();
    jdbc.ifPresent(repos -> repos.persistedGrants().deleteBySubject(userId));
    if (dialog(request)) {
      return ResponseEntity.noContent().build();
    }
    return new RedirectView("/grants", true);
  }

  private static boolean dialog(HttpServletRequest request) {
    return "1".equals(request.getParameter("dialog"));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String when(java.time.Instant instant) {
    if (instant == null) {
      return "—";
    }
    return TIME.format(instant);
  }
}
