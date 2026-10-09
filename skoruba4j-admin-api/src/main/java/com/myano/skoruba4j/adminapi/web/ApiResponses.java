package com.myano.skoruba4j.adminapi.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class ApiResponses {
  private ApiResponses() {}

  static ResponseEntity<Map<String, String>> noDatabase() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("error", "database not configured"));
  }

  static ResponseEntity<Map<String, String>> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not found"));
  }

  static ResponseEntity<Map<String, String>> badRequest(String message) {
    return ResponseEntity.badRequest().body(Map.of("error", message));
  }

  /**
   * ASP.NET Identity Admin API validation body. 4S {@code IdentityService} only surfaces HTTP 400
   * when the JSON has {@code errors}; other shapes are swallowed and later NRE on a null user.
   */
  static ResponseEntity<Map<String, Object>> validation(String field, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "One or more validation errors occurred.");
    body.put("status", 400);
    body.put("errors", Map.of(field, List.of(message)));
    return ResponseEntity.badRequest().body(body);
  }
}
