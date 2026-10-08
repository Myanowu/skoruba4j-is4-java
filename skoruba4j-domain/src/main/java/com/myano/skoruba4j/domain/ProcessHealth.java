package com.myano.skoruba4j.domain;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Public health JSON for the ops console. No secrets. */
public final class ProcessHealth {
  private ProcessHealth() {}

  public static Map<String, String> snapshot(
      String process, String issuer, Optional<JdbcRepositories> jdbc) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("status", "UP");
    body.put("process", process == null ? "" : process);
    body.put("issuer", issuer == null ? "" : issuer.trim());
    if (jdbc == null || jdbc.isEmpty()) {
      body.put("database", "not-configured");
      return body;
    }
    try {
      jdbc.get().ping();
      body.put("database", "UP");
    } catch (UncheckedSqlException e) {
      body.put("database", "DOWN");
    }
    return body;
  }
}
