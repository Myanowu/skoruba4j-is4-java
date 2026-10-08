package com.myano.skoruba4j.console.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JdbcUrlComposerTest {

  @Test
  void roundTripSqlServer() {
    String url =
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=IdentityServer;encrypt=true;trustServerCertificate=true;loginTimeout=15";
    JdbcUrlComposer.Parts parts = JdbcUrlComposer.parse("sqlserver", url, Path.of("."));
    assertFalse(parts.rawOnly);
    assertEquals("127.0.0.1", parts.host);
    assertEquals("1433", parts.port);
    assertEquals("IdentityServer", parts.database);
    assertTrue(parts.extra.contains("encrypt=true"));
    assertEquals(url, JdbcUrlComposer.compose(parts, Path.of(".")));
  }

  @Test
  void roundTripSqlitePortable() {
    String url = "jdbc:sqlite:${IDSERVER_HOME}/data/skoruba4j.sqlite?journal_mode=WAL&busy_timeout=8000";
    JdbcUrlComposer.Parts parts = JdbcUrlComposer.parse("sqlite", url, Path.of("C:/install"));
    assertEquals(JdbcUrlComposer.portableSqliteFile(), parts.sqliteFile);
    assertEquals(url, JdbcUrlComposer.compose(parts, Path.of("C:/install")));
  }

  @Test
  void composePostgres() {
    JdbcUrlComposer.Parts parts = new JdbcUrlComposer.Parts();
    parts.provider = "postgresql";
    parts.host = "db.local";
    parts.port = "5432";
    parts.database = "idserver";
    parts.extra = "sslmode=require";
    assertEquals(
        "jdbc:postgresql://db.local:5432/idserver?sslmode=require",
        JdbcUrlComposer.compose(parts, null));
  }

  @Test
  void driverClassForSqlite() {
    assertEquals("org.sqlite.JDBC", JdbcUrlComposer.driverClass("sqlite"));
  }

  @Test
  void sqlServerDropsSqliteExtraAndAddsTrust() {
    JdbcUrlComposer.Parts parts = new JdbcUrlComposer.Parts();
    parts.provider = "sqlserver";
    parts.host = "127.0.0.1";
    parts.port = "1433";
    parts.database = "IdentityServer";
    parts.extra = JdbcUrlComposer.SQLITE_DEFAULT_EXTRA;
    assertEquals(
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=IdentityServer;encrypt=true;trustServerCertificate=true;loginTimeout=15",
        JdbcUrlComposer.compose(parts, null));
  }

  @Test
  void staleExtraDetectsSqliteDefaultsOnSqlServer() {
    assertTrue(JdbcUrlComposer.isStaleExtra("sqlserver", JdbcUrlComposer.SQLITE_DEFAULT_EXTRA));
    assertFalse(
        JdbcUrlComposer.isStaleExtra("sqlserver", JdbcUrlComposer.SQLSERVER_DEFAULT_EXTRA));
  }

  @Test
  void normalizeRepairsDirtySqlServerUrl() {
    String dirty =
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=IdentityServer;journal_mode=WAL&busy_timeout=8000";
    assertEquals(
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=IdentityServer;encrypt=true;trustServerCertificate=true;loginTimeout=15",
        JdbcUrlComposer.normalize("sqlserver", dirty, null));
  }
}
