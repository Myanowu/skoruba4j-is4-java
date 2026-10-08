package com.myano.skoruba4j.domain.jdbc;

import com.myano.skoruba4j.domain.DbProvider;

public final class SqlDialect {
  private final DbProvider provider;

  public SqlDialect(DbProvider provider) {
    this.provider = provider;
  }

  public DbProvider provider() {
    return provider;
  }

  public String quote(String identifier) {
    return switch (provider) {
      case SQLSERVER -> "[" + identifier.replace("]", "]]") + "]";
      case POSTGRESQL, SQLITE -> "\"" + identifier.replace("\"", "\"\"") + "\"";
      case MYSQL -> "`" + identifier.replace("`", "``") + "`";
    };
  }

  public String selectLimited(String columns, String fromAndRest, int limit) {
    int n = Math.max(1, limit);
    return switch (provider) {
      case SQLSERVER -> "SELECT TOP (" + n + ") " + columns + " " + fromAndRest;
      case POSTGRESQL, MYSQL, SQLITE -> "SELECT " + columns + " " + fromAndRest + " LIMIT " + n;
    };
  }

  public String selectPaged(String columns, String fromWhere, String orderBy, int offset, int limit) {
    return selectPaged(columns, fromWhere, orderBy, true, offset, limit);
  }

  public String selectPaged(
      String columns, String fromWhere, String orderBy, boolean ascending, int offset, int limit) {
    int lim = Math.max(1, limit);
    int off = Math.max(0, offset);
    String dir = ascending ? "ASC" : "DESC";
    return switch (provider) {
      case SQLSERVER ->
          "SELECT "
              + columns
              + " "
              + fromWhere
              + " ORDER BY "
              + orderBy
              + " "
              + dir
              + " OFFSET "
              + off
              + " ROWS FETCH NEXT "
              + lim
              + " ROWS ONLY";
      case POSTGRESQL, MYSQL, SQLITE ->
          "SELECT "
              + columns
              + " "
              + fromWhere
              + " ORDER BY "
              + orderBy
              + " "
              + dir
              + " LIMIT "
              + lim
              + " OFFSET "
              + off;
    };
  }
}
