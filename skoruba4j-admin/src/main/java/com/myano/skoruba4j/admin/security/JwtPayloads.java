package com.myano.skoruba4j.admin.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads JWT JSON payloads without verifying signatures (display / claim merge only). */
public final class JwtPayloads {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private static final TypeReference<LinkedHashMap<String, Object>> MAP =
      new TypeReference<>() {};

  private JwtPayloads() {}

  /** Returns JWT payload claims, or an empty map when the value is not a JWT. */
  public static Map<String, Object> parse(String jwt) {
    String json = payloadJson(jwt);
    if (json == null) {
      return Map.of();
    }
    try {
      LinkedHashMap<String, Object> claims = MAPPER.readValue(json, MAP);
      return claims == null ? Map.of() : claims;
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  /** Pretty-prints a claims map for the User info dialog. */
  public static String pretty(Map<String, Object> claims) {
    if (claims == null || claims.isEmpty()) {
      return "";
    }
    try {
      return MAPPER.writeValueAsString(claims);
    } catch (JsonProcessingException e) {
      return claims.toString();
    }
  }

  private static String payloadJson(String jwt) {
    if (jwt == null || jwt.isBlank()) {
      return null;
    }
    String[] parts = jwt.split("\\.");
    if (parts.length < 2) {
      return null;
    }
    try {
      return new String(Base64.getUrlDecoder().decode(pad(parts[1])), StandardCharsets.UTF_8);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String pad(String value) {
    int mod = value.length() % 4;
    if (mod == 0) {
      return value;
    }
    return value + "====".substring(mod);
  }
}
