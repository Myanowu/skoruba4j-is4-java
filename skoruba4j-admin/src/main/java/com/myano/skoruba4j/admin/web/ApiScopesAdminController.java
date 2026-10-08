package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/api-scopes")
public class ApiScopesAdminController extends DiscoveryResourceAdminController {
  public ApiScopesAdminController(Optional<JdbcRepositories> jdbc) {
    super(jdbc, JdbcRepositories::apiScopes, "/admin/api-scopes", "apiScopes.title", "apiScopes.title");
  }
}
