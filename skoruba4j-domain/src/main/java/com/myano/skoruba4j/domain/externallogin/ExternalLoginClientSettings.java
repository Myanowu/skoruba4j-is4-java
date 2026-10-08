package com.myano.skoruba4j.domain.externallogin;

import com.myano.skoruba4j.domain.configstore.ClientConfiguration;
import java.util.List;
import java.util.Locale;

/**
 * Per-OAuth-Client external login flags stored in {@code ClientProperties} (no schema change).
 *
 * <ul>
 *   <li>{@code skoruba4j.external.google} / {@code .link-mode}
 *   <li>{@code skoruba4j.external.microsoft} / {@code .link-mode}
 *   <li>{@code skoruba4j.external.whatsapp} / {@code .link-mode}
 *   <li>{@code skoruba4j.external.wechat} / {@code .link-mode}
 * </ul>
 */
public final class ExternalLoginClientSettings {
  public static final String GOOGLE_ENABLED_KEY = "skoruba4j.external.google";
  public static final String GOOGLE_LINK_MODE_KEY = "skoruba4j.external.google.link-mode";
  public static final String MICROSOFT_ENABLED_KEY = "skoruba4j.external.microsoft";
  public static final String MICROSOFT_LINK_MODE_KEY = "skoruba4j.external.microsoft.link-mode";
  public static final String WHATSAPP_ENABLED_KEY = "skoruba4j.external.whatsapp";
  public static final String WHATSAPP_LINK_MODE_KEY = "skoruba4j.external.whatsapp.link-mode";
  public static final String WECHAT_ENABLED_KEY = "skoruba4j.external.wechat";
  public static final String WECHAT_LINK_MODE_KEY = "skoruba4j.external.wechat.link-mode";

  private final boolean googleEnabled;
  private final ExternalLoginLinkMode googleLinkMode;
  private final boolean microsoftEnabled;
  private final ExternalLoginLinkMode microsoftLinkMode;
  private final boolean whatsappEnabled;
  private final ExternalLoginLinkMode whatsappLinkMode;
  private final boolean wechatEnabled;
  private final ExternalLoginLinkMode wechatLinkMode;

  public ExternalLoginClientSettings(
      boolean googleEnabled,
      ExternalLoginLinkMode googleLinkMode,
      boolean microsoftEnabled,
      ExternalLoginLinkMode microsoftLinkMode,
      boolean whatsappEnabled,
      ExternalLoginLinkMode whatsappLinkMode,
      boolean wechatEnabled,
      ExternalLoginLinkMode wechatLinkMode) {
    this.googleEnabled = googleEnabled;
    this.googleLinkMode =
        googleLinkMode == null ? ExternalLoginLinkMode.LINK_EXISTING : googleLinkMode;
    this.microsoftEnabled = microsoftEnabled;
    this.microsoftLinkMode =
        microsoftLinkMode == null ? ExternalLoginLinkMode.LINK_EXISTING : microsoftLinkMode;
    this.whatsappEnabled = whatsappEnabled;
    this.whatsappLinkMode =
        whatsappLinkMode == null ? ExternalLoginLinkMode.LINK_EXISTING : whatsappLinkMode;
    this.wechatEnabled = wechatEnabled;
    this.wechatLinkMode =
        wechatLinkMode == null ? ExternalLoginLinkMode.LINK_EXISTING : wechatLinkMode;
  }

  /** Defaults: all providers off, link-existing. */
  public static ExternalLoginClientSettings disabled() {
    return new ExternalLoginClientSettings(
        false,
        ExternalLoginLinkMode.LINK_EXISTING,
        false,
        ExternalLoginLinkMode.LINK_EXISTING,
        false,
        ExternalLoginLinkMode.LINK_EXISTING,
        false,
        ExternalLoginLinkMode.LINK_EXISTING);
  }

  public boolean googleEnabled() {
    return googleEnabled;
  }

  public ExternalLoginLinkMode googleLinkMode() {
    return googleLinkMode;
  }

  public boolean microsoftEnabled() {
    return microsoftEnabled;
  }

  public ExternalLoginLinkMode microsoftLinkMode() {
    return microsoftLinkMode;
  }

  public boolean whatsappEnabled() {
    return whatsappEnabled;
  }

  public ExternalLoginLinkMode whatsappLinkMode() {
    return whatsappLinkMode;
  }

  public boolean wechatEnabled() {
    return wechatEnabled;
  }

  public ExternalLoginLinkMode wechatLinkMode() {
    return wechatLinkMode;
  }

  public static ExternalLoginClientSettings from(ClientConfiguration client) {
    if (client == null) {
      return disabled();
    }
    return from(client.properties());
  }

  public static ExternalLoginClientSettings from(
      List<ClientConfiguration.ClientProperty> properties) {
    boolean google = false;
    ExternalLoginLinkMode googleMode = ExternalLoginLinkMode.LINK_EXISTING;
    boolean microsoft = false;
    ExternalLoginLinkMode microsoftMode = ExternalLoginLinkMode.LINK_EXISTING;
    boolean whatsapp = false;
    ExternalLoginLinkMode whatsappMode = ExternalLoginLinkMode.LINK_EXISTING;
    boolean wechat = false;
    ExternalLoginLinkMode wechatMode = ExternalLoginLinkMode.LINK_EXISTING;
    if (properties != null) {
      for (ClientConfiguration.ClientProperty property : properties) {
        if (property == null || property.key() == null) {
          continue;
        }
        String key = property.key().trim();
        String value = property.value() == null ? "" : property.value().trim();
        if (GOOGLE_ENABLED_KEY.equalsIgnoreCase(key)) {
          google = isTruthy(value);
        } else if (GOOGLE_LINK_MODE_KEY.equalsIgnoreCase(key)) {
          googleMode = ExternalLoginLinkMode.fromConfig(value);
        } else if (MICROSOFT_ENABLED_KEY.equalsIgnoreCase(key)) {
          microsoft = isTruthy(value);
        } else if (MICROSOFT_LINK_MODE_KEY.equalsIgnoreCase(key)) {
          microsoftMode = ExternalLoginLinkMode.fromConfig(value);
        } else if (WHATSAPP_ENABLED_KEY.equalsIgnoreCase(key)) {
          whatsapp = isTruthy(value);
        } else if (WHATSAPP_LINK_MODE_KEY.equalsIgnoreCase(key)) {
          whatsappMode = ExternalLoginLinkMode.fromConfig(value);
        } else if (WECHAT_ENABLED_KEY.equalsIgnoreCase(key)) {
          wechat = isTruthy(value);
        } else if (WECHAT_LINK_MODE_KEY.equalsIgnoreCase(key)) {
          wechatMode = ExternalLoginLinkMode.fromConfig(value);
        }
      }
    }
    return new ExternalLoginClientSettings(
        google,
        googleMode,
        microsoft,
        microsoftMode,
        whatsapp,
        whatsappMode,
        wechat,
        wechatMode);
  }

  private static boolean isTruthy(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String v = value.trim().toLowerCase(Locale.ROOT);
    return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
  }
}
