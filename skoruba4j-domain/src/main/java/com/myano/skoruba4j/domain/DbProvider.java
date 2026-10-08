package com.myano.skoruba4j.domain;

import java.util.Locale;

public enum DbProvider {
  SQLSERVER,
  POSTGRESQL,
  MYSQL,
  SQLITE;

  public static DbProvider fromConfig(String value) {
    if (value == null || value.isBlank()) {
      return SQLSERVER;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "sqlserver", "mssql" -> SQLSERVER;
      case "postgresql", "postgres" -> POSTGRESQL;
      case "mysql" -> MYSQL;
      case "sqlite" -> SQLITE;
      default -> throw new IllegalArgumentException("Unknown db provider: " + value);
    };
  }

  public String driverClassName() {
    return switch (this) {
      case SQLSERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
      case POSTGRESQL -> "org.postgresql.Driver";
      case MYSQL -> "com.mysql.cj.jdbc.Driver";
      case SQLITE -> "org.sqlite.JDBC";
    };
  }
}
