package com.myano.skoruba4j.domain;

import java.util.Locale;

public enum TableStyle {
  SKORUBA,
  ASPNET;

  public static TableStyle fromConfig(String value) {
    if (value == null || value.isBlank()) {
      return SKORUBA;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "skoruba" -> SKORUBA;
      case "aspnet" -> ASPNET;
      default -> throw new IllegalArgumentException("Unknown table-style: " + value);
    };
  }
}
