package com.myano.skoruba4j.sts.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccountChooserPageTest {

  @Test
  void rendersContinueAndUseAnother() {
    String html =
        AccountChooserPage.render(
            "Alice",
            "alice@example.com",
            "/connect/authorize?response_type=code&client_id=test-admin&redirect_uri=http://127.0.0.1/cb&scope=openid",
            "test-admin");
    assertTrue(html.contains("Choose an account"));
    assertTrue(html.contains("Continue to test-admin"));
    assertTrue(html.contains("Alice"));
    assertTrue(html.contains("alice@example.com"));
    assertTrue(html.contains("/login/choose/continue?ReturnUrl="));
    assertTrue(html.contains("Use another account"));
    assertFalse(html.contains("org.springframework"));
  }
}
