package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.admin.config.IdserverProperties;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import com.myano.skoruba4j.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class HomeController {
  private final IdserverProperties properties;
  private final Optional<JdbcRepositories> jdbc;

  public HomeController(IdserverProperties properties, Optional<JdbcRepositories> jdbc) {
    this.properties = properties;
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/login", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String login(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication,
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "error_description", required = false) String description,
      @RequestParam(value = "denied", required = false) String denied,
      @RequestParam(value = "need", required = false) String need,
      @RequestParam(value = "had", required = false) String had)
      throws java.io.IOException {
    if (signedIn(authentication)) {
      response.sendRedirect("/admin");
      return "";
    }
    String required = need == null || need.isBlank() ? properties.adminRole() : need;
    return AdminHtml.loginPage(
        Messages.t("login.title"),
        AdminRequests.flash(request),
        AdminHtml.loginForm(
            AdminRequests.csrfName(request),
            AdminRequests.csrfToken(request),
            error,
            description,
            properties.loginMode(),
            properties.issuerUri() + "/forgot-password"),
        denied != null,
        required,
        had);
  }

  @GetMapping("/signout-callback-oidc")
  public RedirectView signedOut() {
    RedirectView login = new RedirectView("/login");
    login.setContextRelative(true);
    return login;
  }

  @GetMapping(value = {"/", "/admin"}, produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String home(HttpServletRequest request, Authentication authentication) {
    StringBuilder body = new StringBuilder();
    body.append("<h1>").append(AdminHtml.esc(Messages.t("home.title"))).append("</h1>");
    body.append("<p class=\"lead\">")
        .append(AdminHtml.esc(Messages.t("home.lead")))
        .append("</p>");
    body.append("<p class=\"chips\"><span class=\"chip\">table-style ")
        .append(AdminHtml.esc(properties.tableStyle().name().toLowerCase()))
        .append("</span><span class=\"chip\">provider ")
        .append(AdminHtml.esc(properties.dbProvider().name().toLowerCase()))
        .append("</span><span class=\"chip\">login ")
        .append(AdminHtml.esc(properties.loginMode().configValue()))
        .append("</span><span class=\"chip\">admin-role ")
        .append(AdminHtml.esc(properties.adminRole()))
        .append("</span></p>");
    if (signedIn(authentication)) {
      body.append("<p class=\"ok\">")
          .append(AdminHtml.esc(Messages.t("home.signedIn", authentication.getName())))
          .append("</p>");
    }
    if (jdbc.isEmpty()) {
      body.append("<p class=\"warn\">")
          .append(AdminHtml.esc(Messages.t("home.dbMissing")))
          .append("</p>");
    } else {
      try {
        JdbcRepositories repos = jdbc.get();
        repos.ping();
        if (properties.dbProvider() == com.myano.skoruba4j.domain.DbProvider.SQLITE) {
          body.append("<p class=\"warn\">")
              .append(AdminHtml.esc(Messages.t("home.sqliteWarn")))
              .append("</p>");
        }
        body.append("<p class=\"ok\">")
            .append(
                AdminHtml.esc(
                    Messages.t("home.connected", repos.identityTables().users())))
            .append("</p>");
        body.append("<div class=\"stats\">");
        body.append("<a class=\"stat\" href=\"/admin/clients\"><strong>")
            .append(repos.clients().count())
            .append("</strong><span>")
            .append(AdminHtml.esc(Messages.t("nav.clients")))
            .append("</span></a>");
        body.append("<a class=\"stat\" href=\"/admin/users\"><strong>")
            .append(repos.users().count())
            .append("</strong><span>")
            .append(AdminHtml.esc(Messages.t("nav.users")))
            .append("</span></a>");
        body.append("<a class=\"stat\" href=\"/admin/api-resources\"><strong>")
            .append(repos.apiResources().count())
            .append("</strong><span>")
            .append(AdminHtml.esc(Messages.t("nav.apiResources")))
            .append("</span></a>");
        body.append("</div>");
      } catch (UncheckedSqlException e) {
        body.append("<p class=\"err\">")
            .append(AdminHtml.esc(e.getMessage()))
            .append("</p>");
      }
    }
    body.append(AdminHtml.guide());
    return AdminHtml.page(
        Messages.t("home.title"),
        AdminRequests.flash(request),
        AdminRequests.csrfName(request),
        AdminRequests.csrfToken(request),
        body.toString());
  }

  private static boolean signedIn(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }
}
