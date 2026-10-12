package com.myano.orgplatform.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds columns introduced after the first org_person table, without a migration tool. */
@Component
public class PersonSchemaPatch {

  private final JdbcTemplate jdbc;

  public PersonSchemaPatch(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Ensures {@code login_name} exists on databases created before that column. */
  @PostConstruct
  public void addLoginName() {
    List<String> names =
        jdbc.query("PRAGMA table_info(org_person)", (rs, i) -> rs.getString("name"));
    if (names.isEmpty() || names.contains("login_name")) {
      return;
    }
    jdbc.execute("ALTER TABLE org_person ADD COLUMN login_name TEXT");
  }
}
