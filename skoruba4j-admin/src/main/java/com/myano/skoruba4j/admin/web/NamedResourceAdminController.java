package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.ApiResourceSummary;
import com.myano.skoruba4j.domain.configstore.NamedResourceRepository;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
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

abstract class NamedResourceAdminController {
  private final Optional<JdbcRepositories> jdbc;
  private final Function<JdbcRepositories, NamedResourceRepository> repo;
  private final String path;
  private final String title;

  NamedResourceAdminController(
      Optional<JdbcRepositories> jdbc,
      Function<JdbcRepositories, NamedResourceRepository> repo,
      String path,
      String title) {
    this.jdbc = jdbc;
    this.repo = repo;
    this.path = path;
    this.title = title;
  }

  @GetMapping(produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String list(
      HttpServletRequest request,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String searchText,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page) {
    PageQuery query = PageQuery.of(searchText, page, 20);
    String csrf = AdminHtml.hiddenCsrf(AdminRequests.csrfName(request), AdminRequests.csrfToken(request));
    StringBuilder body = new StringBuilder("<h1>").append(AdminHtml.esc(title)).append("</h1>");
    body.append(AdminHtml.searchBar(path, query.searchText(), query.page()));
    body.append("<form method=\"post\" action=\"").append(path).append("\">").append(csrf);
    body.append("<label>Name</label><input name=\"name\" required>");
    body.append("<label>DisplayName</label><input name=\"displayName\">");
    body.append("<p><label><input type=\"checkbox\" name=\"enabled\" checked> Enabled</label></p>");
    body.append("<p><button type=\"submit\">Create</button></p></form>");
    PageResult<ApiResourceSummary> result = repo().search(query);
    body.append("<table><tr><th>Id</th><th>Name</th><th>DisplayName</th><th>Enabled</th><th></th></tr>");
    for (ApiResourceSummary item : result.items()) {
      body.append("<tr><td>")
          .append(item.id())
          .append("</td><td>")
          .append(AdminHtml.esc(item.name()))
          .append("</td><td>")
          .append(AdminHtml.esc(item.displayName()))
          .append("</td><td>")
          .append(item.enabled())
          .append("</td><td><form method=\"post\" action=\"")
          .append(path)
          .append("/")
          .append(item.id())
          .append("/delete\" style=\"display:inline\">")
          .append(csrf)
          .append("<button type=\"submit\">Delete</button></form></td></tr>");
    }
    body.append("</table>");
    body.append(AdminHtml.pager(path, query.searchText(), result.page(), result.pageSize(), result.totalCount()));
    return AdminHtml.page(
        title,
        AdminRequests.flash(request),
        AdminRequests.csrfName(request),
        AdminRequests.csrfToken(request),
        body.toString());
  }

  @PostMapping
  public RedirectView create(HttpServletRequest request, RedirectAttributes redirect) {
    repo()
        .insert(
            request.getParameter("name"),
            request.getParameter("displayName"),
            AdminRequests.checked(request, "enabled"));
    AdminRequests.notice(redirect, "Created");
    return new RedirectView(path, true);
  }

  @PostMapping("/{id}/delete")
  public RedirectView delete(@PathVariable int id, RedirectAttributes redirect) {
    repo().delete(id);
    AdminRequests.notice(redirect, "Deleted");
    return new RedirectView(path, true);
  }

  private NamedResourceRepository repo() {
    JdbcRepositories repos =
        jdbc.orElseThrow(
            () -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "database not configured"));
    return this.repo.apply(repos);
  }
}
