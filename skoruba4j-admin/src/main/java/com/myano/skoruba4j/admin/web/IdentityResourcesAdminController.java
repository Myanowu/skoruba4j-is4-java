package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/identity-resources")
public class IdentityResourcesAdminController extends DiscoveryResourceAdminController {
  public IdentityResourcesAdminController(Optional<JdbcRepositories> jdbc) {
    super(
        jdbc,
        JdbcRepositories::identityResources,
        "/admin/identity-resources",
        "identityResources.title",
        "identityResources.title");
  }
}
