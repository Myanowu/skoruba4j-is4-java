package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.domain.ProcessHealth;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final Optional<JdbcRepositories> jdbc;
  private final String process;
  private final String issuer;

  public HealthController(
      Optional<JdbcRepositories> jdbc,
      @Value("${spring.application.name:skoruba4j-admin}") String process,
      @Value("${idserver.issuer-uri:}") String issuer) {
    this.jdbc = jdbc;
    this.process = process;
    this.issuer = issuer;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return ProcessHealth.snapshot(process, issuer, jdbc);
  }
}
