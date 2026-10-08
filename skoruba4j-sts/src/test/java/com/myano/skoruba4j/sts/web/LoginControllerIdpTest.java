package com.myano.skoruba4j.sts.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LoginControllerIdpTest {

  @Test
  void readsIdpFromAuthorizeReturnUrl() {
    assertEquals(
        "whatsapp",
        LoginController.idpFromUrl(
            "/connect/authorize?client_id=skoruba4j-admin-api&idp=whatsapp&scope=openid"));
    assertEquals("wechat", LoginController.firstIdp(null, "/connect/authorize?idp=wechat"));
    assertEquals("google", LoginController.firstIdp("google", "/connect/authorize?idp=whatsapp"));
    assertNull(LoginController.idpFromUrl("/connect/authorize?client_id=x"));
  }
}
