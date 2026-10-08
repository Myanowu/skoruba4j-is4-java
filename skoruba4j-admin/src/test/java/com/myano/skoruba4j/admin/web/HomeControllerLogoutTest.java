package com.myano.skoruba4j.admin.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.myano.skoruba4j.admin.config.IdserverProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.view.RedirectView;

class HomeControllerLogoutTest {

  @Test
  void oidcLogoutReturnsToTheAdminLogin() {
    IdserverProperties properties = new IdserverProperties();
    properties.getAdmin().setOidcEnabled(true);
    RedirectView view = new HomeController(properties, Optional.empty()).signedOut();
    assertEquals("/login", view.getUrl());
  }

  @Test
  void localLogoutReturnsToTheAdminLogin() {
    IdserverProperties properties = new IdserverProperties();
    properties.getAdmin().setOidcEnabled(false);
    properties.setIssuerUri("https://localhost:5051");
    RedirectView view = new HomeController(properties, Optional.empty()).signedOut();
    assertEquals("/login", view.getUrl());
  }
}
