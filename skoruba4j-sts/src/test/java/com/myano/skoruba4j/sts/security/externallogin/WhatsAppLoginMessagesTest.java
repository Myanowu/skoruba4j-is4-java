package com.myano.skoruba4j.sts.security.externallogin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WhatsAppLoginMessagesTest {

  @Test
  void extractsCodeFromPrefillAndVariants() {
    assertEquals("ABCD2345", WhatsAppLoginMessages.extractCode("LOGIN ABCD2345").orElseThrow());
    assertEquals("ABCD2345", WhatsAppLoginMessages.extractCode("login%ABCD2345").orElseThrow());
    assertEquals("ABCD2345", WhatsAppLoginMessages.extractCode("Please LOGIN_ABCD2345 thanks").orElseThrow());
    assertTrue(WhatsAppLoginMessages.extractCode("hello").isEmpty());
    assertEquals("LOGIN ABCD2345", WhatsAppLoginMessages.prefillText("abcd2345"));
  }
}
