package com.myano.skoruba4j.adminapi.web.ui;

import com.myano.skoruba4j.adminapi.web.ui.ApiDebugPresets.ApiSection;
import com.myano.skoruba4j.adminapi.web.ui.ApiUiHtml.DebugEndpoint;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Left-menu API explorer: pick an object, list its endpoints, run them in the debug panel. */
@Controller
@ConditionalOnProperty(
    prefix = "idserver.admin",
    name = "api-ui-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class UiApiExplorerController {

  @GetMapping(value = "/ui/api", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String api(@RequestParam(required = false) String resource) {
    Optional<ApiSection> section = ApiDebugPresets.find(resource);
    if (section.isEmpty()) {
      StringBuilder body = new StringBuilder();
      body.append("<h1>API</h1>");
      body.append(
          "<p class=\"muted\">Choose an object in the left API menu to list endpoints for that resource."
              + " Selecting an operation fills the debug form below.</p>");
      body.append("<div class=\"grid\">");
      for (ApiSection s : ApiDebugPresets.sections()) {
        body.append("<a class=\"tile\" href=\"")
            .append(ApiUiHtml.esc(s.href()))
            .append("\"><strong>")
            .append(ApiUiHtml.esc(s.label()))
            .append("</strong><span class=\"muted\">")
            .append(s.endpoints().size())
            .append(" operations</span></a>");
      }
      body.append("</div>");
      return ApiUiHtml.page("API", null, body.toString(), null, List.of());
    }
    ApiSection s = section.get();
    List<DebugEndpoint> endpoints = s.endpoints();
    StringBuilder body = new StringBuilder();
    body.append("<h1>").append(ApiUiHtml.esc(s.label())).append(" API</h1>");
    body.append("<p class=\"muted\">")
        .append(endpoints.size())
        .append(" operations for this object. Click one in the left menu to load it.</p>");
    body.append("<table><thead><tr><th>Method</th><th>Label</th><th>Path</th></tr></thead><tbody>");
    for (DebugEndpoint ep : endpoints) {
      body.append("<tr><td><code>")
          .append(ApiUiHtml.esc(ep.method()))
          .append("</code></td><td>")
          .append(ApiUiHtml.esc(ep.label()))
          .append("</td><td><code>")
          .append(ApiUiHtml.esc(ep.path()))
          .append("</code></td></tr>");
    }
    body.append("</tbody></table>");
    String listHref = ApiDebugPresets.dataHref(s.key());
    if (listHref != null) {
      body.append("<p class=\"muted\">Data list: <a href=\"")
          .append(ApiUiHtml.esc(listHref))
          .append("\">")
          .append(ApiUiHtml.esc(s.label()))
          .append("</a></p>");
    }
    return ApiUiHtml.page(s.label() + " API", null, body.toString(), s.key(), endpoints);
  }
}
