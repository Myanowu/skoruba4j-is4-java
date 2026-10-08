package com.myano.skoruba4j.domain.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.DbProvider;
import org.junit.jupiter.api.Test;

class SqlDialectTest {

  @Test
  void sqlServerQuotesAndTop() {
    SqlDialect d = new SqlDialect(DbProvider.SQLSERVER);
    assertEquals("[Users]", d.quote("Users"));
    String sql = d.selectLimited("[ClientId]", "FROM [Clients] ORDER BY [ClientId]", 20);
    assertTrue(sql.startsWith("SELECT TOP (20) "));
  }

  @Test
  void postgresQuotesAndLimit() {
    SqlDialect d = new SqlDialect(DbProvider.POSTGRESQL);
    assertEquals("\"Users\"", d.quote("Users"));
    String sql = d.selectLimited("\"ClientId\"", "FROM \"Clients\" ORDER BY \"ClientId\"", 20);
    assertTrue(sql.endsWith(" LIMIT 20"));
  }

  @Test
  void sqlServerOffsetFetch() {
    SqlDialect d = new SqlDialect(DbProvider.SQLSERVER);
    String sql = d.selectPaged("[Id]", "FROM [Clients]", "[ClientId]", 10, 5);
    assertTrue(sql.contains("OFFSET 10 ROWS FETCH NEXT 5 ROWS ONLY"));
  }

  @Test
  void sqliteQuotesAndLimit() {
    SqlDialect d = new SqlDialect(DbProvider.SQLITE);
    assertEquals("\"Users\"", d.quote("Users"));
    String sql = d.selectLimited("\"ClientId\"", "FROM \"Clients\" ORDER BY \"ClientId\"", 20);
    assertTrue(sql.endsWith(" LIMIT 20"));
  }

  @Test
  void sqlitePagedDescendingDoesNotDoubleDirection() {
    SqlDialect d = new SqlDialect(DbProvider.SQLITE);
    String sql = d.selectPaged("\"Key\"", "FROM \"PersistedGrants\"", "\"CreationTime\"", false, 0, 20);
    assertTrue(sql.contains("ORDER BY \"CreationTime\" DESC LIMIT 20 OFFSET 0"));
    assertTrue(!sql.contains("DESC ASC"));
  }
}
