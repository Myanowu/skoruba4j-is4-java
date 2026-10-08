package com.myano.skoruba4j.admin.web;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * UI-only presets for New client. Not stored in the database — only pre-fills the create form
 * (same idea as Skoruba Admin client type cards).
 */
enum ClientCreateTemplate {
  EMPTY(
      "empty",
      "Empty",
      "Empty — default",
      "Start from a blank-ish authorization_code client; tune everything below.",
      "authorization_code",
      "openid\nprofile",
      true,
      true,
      false),
  SPA(
      "spa",
      "SPA",
      "Single-page app (JavaScript)",
      "Authorization Code + PKCE · public client (no secret).",
      "authorization_code",
      "openid\nprofile\noffline_access",
      false,
      true,
      true),
  WEB(
      "web",
      "Web",
      "Web app — server-side",
      "Authorization Code + PKCE · confidential client.",
      "authorization_code",
      "openid\nprofile\noffline_access",
      true,
      true,
      true),
  NATIVE(
      "native",
      "Native",
      "Native — mobile / desktop",
      "Authorization Code + PKCE · public client.",
      "authorization_code",
      "openid\nprofile\noffline_access",
      false,
      true,
      true),
  DEVICE(
      "device",
      "Device",
      "TV / limited-input device",
      "Device authorization grant. STS support may still be partial — verify before production.",
      "urn:ietf:params:oauth:grant-type:device_code",
      "openid\nprofile",
      false,
      false,
      false),
  MACHINE(
      "machine",
      "Machine",
      "Machine / service",
      "Client credentials only. No interactive login; add API scopes as needed.",
      "client_credentials",
      "",
      true,
      false,
      false);

  private final String id;
  private final String shortLabel;
  private final String title;
  private final String subtitle;
  private final String grantTypes;
  private final String scopes;
  private final boolean requireClientSecret;
  private final boolean requirePkce;
  private final boolean allowOfflineAccess;

  ClientCreateTemplate(
      String id,
      String shortLabel,
      String title,
      String subtitle,
      String grantTypes,
      String scopes,
      boolean requireClientSecret,
      boolean requirePkce,
      boolean allowOfflineAccess) {
    this.id = id;
    this.shortLabel = shortLabel;
    this.title = title;
    this.subtitle = subtitle;
    this.grantTypes = grantTypes;
    this.scopes = scopes;
    this.requireClientSecret = requireClientSecret;
    this.requirePkce = requirePkce;
    this.allowOfflineAccess = allowOfflineAccess;
  }

  String id() {
    return id;
  }

  String shortLabel() {
    return shortLabel;
  }

  String title() {
    return title;
  }

  String subtitle() {
    return subtitle;
  }

  String grantTypes() {
    return grantTypes;
  }

  String scopes() {
    return scopes;
  }

  boolean requireClientSecret() {
    return requireClientSecret;
  }

  boolean requirePkce() {
    return requirePkce;
  }

  boolean allowOfflineAccess() {
    return allowOfflineAccess;
  }

  static ClientCreateTemplate fromQuery(String raw) {
    if (raw == null || raw.isBlank()) {
      return EMPTY;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (ClientCreateTemplate t : values()) {
      if (t.id.equals(key)) {
        return t;
      }
    }
    return EMPTY;
  }

  static Map<String, ClientCreateTemplate> allById() {
    Map<String, ClientCreateTemplate> map = new LinkedHashMap<>();
    for (ClientCreateTemplate t : values()) {
      map.put(t.id, t);
    }
    return map;
  }
}
