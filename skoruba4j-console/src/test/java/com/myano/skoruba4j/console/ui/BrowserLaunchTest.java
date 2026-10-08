package com.myano.skoruba4j.console.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BrowserLaunchTest {

  @Test
  void stsAndAdminOpenLogin() {
    assertEquals(
        "https://localhost:5051/login",
        BrowserLaunch.entryUrl("https://localhost:5051/health", "skoruba4j-sts"));
    assertEquals(
        "https://localhost:6061/login",
        BrowserLaunch.entryUrl("https://localhost:6061/health", "skoruba4j-admin"));
  }

  @Test
  void adminApiOpensUiWhenEnabled() {
    assertEquals(
        "https://localhost:44302/login",
        BrowserLaunch.entryUrl("https://localhost:44302/health", "skoruba4j-admin-api", true));
    assertEquals(
        "https://localhost:44302/login",
        BrowserLaunch.entryUrl("https://localhost:44302/health", "skoruba4j-admin-api"));
  }

  @Test
  void adminApiOpensHealthWhenUiDisabled() {
    assertEquals(
        "https://localhost:44302/health",
        BrowserLaunch.entryUrl("https://localhost:44302/health", "skoruba4j-admin-api", false));
  }
}
