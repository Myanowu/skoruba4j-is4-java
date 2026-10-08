package com.myano.skoruba4j.adminapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class StsIdpAuthorizationRequestResolverTest {

  @Test
  void normalizesKnownIdps() {
    assertEquals("whatsapp", StsIdpAuthorizationRequestResolver.normalizeIdp("WhatsApp"));
    assertEquals("wechat", StsIdpAuthorizationRequestResolver.normalizeIdp("wechat"));
    assertNull(StsIdpAuthorizationRequestResolver.normalizeIdp("facebook"));
  }
}
