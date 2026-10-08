package com.myano.skoruba4j.admin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminLoginModeTest {

  @Test
  void fromConfigPrefersLoginMode() {
    assertEquals(AdminLoginMode.STS_PASSWORD, AdminLoginMode.fromConfig("sts-password", true));
    assertEquals(AdminLoginMode.LOCAL, AdminLoginMode.fromConfig("local", true));
    assertEquals(AdminLoginMode.STS_OIDC, AdminLoginMode.fromConfig("sts-oidc", false));
  }

  @Test
  void blankFallsBackToOidcEnabled() {
    assertEquals(AdminLoginMode.STS_OIDC, AdminLoginMode.fromConfig("", true));
    assertEquals(AdminLoginMode.LOCAL, AdminLoginMode.fromConfig(null, false));
  }

  @Test
  void passwordModesShowFormAndStsButton() {
    assertTrue(AdminLoginMode.LOCAL.showsPasswordForm());
    assertTrue(AdminLoginMode.STS_PASSWORD.showsPasswordForm());
    assertTrue(AdminLoginMode.BOTH.showsPasswordForm());
    assertFalse(AdminLoginMode.STS_OIDC.showsPasswordForm());
    assertTrue(AdminLoginMode.LOCAL.usesOidcClientBeans());
    assertTrue(AdminLoginMode.STS_OIDC.usesOidcClientBeans());
    assertTrue(AdminLoginMode.BOTH.usesOidcClientBeans());
    assertEquals(AdminLoginMode.BOTH, AdminLoginMode.fromConfig("both", false));
  }
}
