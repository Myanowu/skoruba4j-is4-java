package com.myano.skoruba4j.sts.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.identity.UserLogin;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountPagesTest {

  @Test
  void homeListsExternalLoginsAndActions() {
    String html =
        SignedInPage.render(
            "id-1",
            "demo",
            "demo@example.com",
            false,
            List.of(new UserLogin("Google", "sub-1", "Google")),
            null);
    assertTrue(html.contains("Signed in"));
    assertTrue(html.contains("Google"));
    assertTrue(html.contains("/account/password"));
    assertTrue(html.contains("openGrants()"));
    assertTrue(html.contains("/account/providers/delete"));
    assertTrue(!html.contains("Open account"));
  }

  @Test
  void registerFormShowsCaptchaWhenPresent() {
    String html = AccountPages.register("Demo IdP", null, "3 + 4 = ?");
    assertTrue(html.contains("Demo IdP"));
    assertTrue(html.contains("3 + 4 = ?"));
    assertTrue(html.contains("name=\"captcha\""));
  }
}
