package com.myano.skoruba4j.adminapi.web.ui;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ConditionalOnProperty(
    prefix = "idserver.admin",
    name = "api-ui-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class UiHomeController {

  @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String home() {
    StringBuilder body = new StringBuilder();
    body.append("<h1>Admin API console</h1>");
    body.append(
        "<p class=\"muted\">Use the left <strong>API</strong> menu to pick an object and run its"
            + " endpoints. JSON machine clients continue to use JWT bearer tokens.</p>");
    body.append("<div class=\"grid\">");
    tile(body, "/ui/api", "API explorer", "All objects & operations");
    tile(body, "/ui/clients", "Clients", "OAuth clients");
    tile(body, "/ui/users", "Users", "Identity users");
    tile(body, "/ui/roles", "Roles", "Identity roles");
    tile(body, "/ui/api-resources", "ApiResources", "API resources / aud");
    tile(body, "/ui/api-scopes", "ApiScopes", "API scopes");
    tile(body, "/ui/identity-resources", "IdentityResources", "OIDC identity resources");
    tile(body, "/ui/grants", "Grants", "Persisted grants");
    body.append("</div>");
    return ApiUiHtml.page("Home", null, body.toString(), null, List.of());
  }

  private static void tile(StringBuilder body, String href, String title, String hint) {
    body.append("<a class=\"tile\" href=\"")
        .append(ApiUiHtml.esc(href))
        .append("\"><strong>")
        .append(ApiUiHtml.esc(title))
        .append("</strong><span class=\"muted\">")
        .append(ApiUiHtml.esc(hint))
        .append("</span></a>");
  }
}
