package com.myano.skoruba4j.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagesTest {

  @AfterEach
  void clear() {
    UiLocale.clearCurrent();
  }

  @Test
  void englishAndChineseDifferForNav() {
    UiLocale.setCurrent(UiLocale.EN);
    String en = Messages.t("nav.clients");
    UiLocale.setCurrent(UiLocale.ZH_HANS);
    String zh = Messages.t("nav.clients");
    assertEquals("Clients", en);
    assertEquals("客户端", zh);
    assertFalse(zh.equals(en));
  }

  @Test
  void traditionalChineseFallbackUsesOwnBundle() {
    UiLocale.setCurrent(UiLocale.ZH_HANT);
    assertEquals("用戶端", Messages.t("nav.clients"));
    assertEquals("總覽", Messages.t("home.title"));
  }

  @Test
  void missingKeyFallsBackToEnglishThenKey() {
    UiLocale.setCurrent(UiLocale.ZH_HANS);
    assertTrue(Messages.t("definitely.missing.key").contains("definitely.missing.key"));
  }
}
