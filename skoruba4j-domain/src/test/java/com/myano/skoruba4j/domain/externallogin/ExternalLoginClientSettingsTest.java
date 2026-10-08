package com.myano.skoruba4j.domain.externallogin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.configstore.ClientConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalLoginClientSettingsTest {

  @Test
  void defaultsOff() {
    ExternalLoginClientSettings settings = ExternalLoginClientSettings.from(List.of());
    assertFalse(settings.googleEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, settings.googleLinkMode());
    assertFalse(settings.microsoftEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, settings.microsoftLinkMode());
    assertFalse(settings.whatsappEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, settings.whatsappLinkMode());
    assertFalse(settings.wechatEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, settings.wechatLinkMode());
  }

  @Test
  void readsGoogleMicrosoftWhatsappAndWechat() {
    ExternalLoginClientSettings settings =
        ExternalLoginClientSettings.from(
            List.of(
                new ClientConfiguration.ClientProperty(
                    1, ExternalLoginClientSettings.GOOGLE_ENABLED_KEY, "true"),
                new ClientConfiguration.ClientProperty(
                    2, ExternalLoginClientSettings.GOOGLE_LINK_MODE_KEY, "auto-create"),
                new ClientConfiguration.ClientProperty(
                    3, ExternalLoginClientSettings.MICROSOFT_ENABLED_KEY, "true"),
                new ClientConfiguration.ClientProperty(
                    4, ExternalLoginClientSettings.MICROSOFT_LINK_MODE_KEY, "confirm"),
                new ClientConfiguration.ClientProperty(
                    5, ExternalLoginClientSettings.WHATSAPP_ENABLED_KEY, "true"),
                new ClientConfiguration.ClientProperty(
                    6, ExternalLoginClientSettings.WHATSAPP_LINK_MODE_KEY, "link-existing"),
                new ClientConfiguration.ClientProperty(
                    7, ExternalLoginClientSettings.WECHAT_ENABLED_KEY, "true"),
                new ClientConfiguration.ClientProperty(
                    8, ExternalLoginClientSettings.WECHAT_LINK_MODE_KEY, "link-existing")));
    assertTrue(settings.googleEnabled());
    assertEquals(ExternalLoginLinkMode.AUTO_CREATE, settings.googleLinkMode());
    assertTrue(settings.microsoftEnabled());
    assertEquals(ExternalLoginLinkMode.CONFIRM, settings.microsoftLinkMode());
    assertTrue(settings.whatsappEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, settings.whatsappLinkMode());
    assertTrue(settings.wechatEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, settings.wechatLinkMode());
  }
}
