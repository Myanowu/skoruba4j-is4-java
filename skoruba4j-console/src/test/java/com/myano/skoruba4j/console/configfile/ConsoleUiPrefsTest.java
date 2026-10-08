package com.myano.skoruba4j.console.configfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.i18n.UiLocale;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsoleUiPrefsTest {

  @AfterEach
  void clearLocale() {
    UiLocale.clearCurrent();
  }

  @Test
  void loadDefaultsToEnglishWhenMissing(@TempDir Path home) {
    assertEquals(UiLocale.EN, ConsoleUiPrefs.load(home));
  }

  @Test
  void saveAndLoadRoundTrip(@TempDir Path home) throws Exception {
    ConsoleUiPrefs.save(home, UiLocale.ZH_HANS);
    Path file = ConsoleUiPrefs.resolve(home);
    assertTrue(Files.isRegularFile(file));
    assertEquals(UiLocale.ZH_HANS, ConsoleUiPrefs.load(home));
    ConsoleUiPrefs.save(home, UiLocale.ZH_HANT);
    assertEquals(UiLocale.ZH_HANT, ConsoleUiPrefs.load(home));
  }

  @Test
  void controlMessagesDifferByLocale() {
    UiLocale.setCurrent(UiLocale.EN);
    String en = Messages.t("control.nav.settings");
    UiLocale.setCurrent(UiLocale.ZH_HANS);
    String zh = Messages.t("control.nav.settings");
    assertEquals("Settings", en);
    assertEquals("设置", zh);
  }
}
