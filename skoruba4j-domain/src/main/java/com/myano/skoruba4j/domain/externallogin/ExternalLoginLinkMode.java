package com.myano.skoruba4j.domain.externallogin;

/** Per-Client policy after a successful external IdP login (e.g. Google). */
public enum ExternalLoginLinkMode {
  /** UserLogins hit or same email → link; else reject. */
  LINK_EXISTING,
  /** Else create Users + UserLogins. */
  AUTO_CREATE,
  /** Else show confirm page then create + link. */
  CONFIRM;

  public static ExternalLoginLinkMode fromConfig(String raw) {
    if (raw == null || raw.isBlank()) {
      return LINK_EXISTING;
    }
    return switch (raw.trim().toLowerCase().replace('_', '-')) {
      case "auto-create", "autocreate", "create" -> AUTO_CREATE;
      case "confirm", "register", "confirmation" -> CONFIRM;
      default -> LINK_EXISTING;
    };
  }

  public String configValue() {
    return switch (this) {
      case LINK_EXISTING -> "link-existing";
      case AUTO_CREATE -> "auto-create";
      case CONFIRM -> "confirm";
    };
  }
}
