package com.myano.skoruba4j.sts.tools;

import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.jdbc.SqlServerToSqlite;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.sqlite.SQLiteDataSource;

/** One-shot local copy. Reads gitignored local-profile YAML; does not print secrets. */
public final class CopySqlServerToSqlite {
  private CopySqlServerToSqlite() {}

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args.length > 1 ? args[1] : "").toAbsolutePath().normalize();
    if (root.toString().isBlank() || !Files.isDirectory(root)) {
      root = Path.of("").toAbsolutePath().normalize();
      if (!Files.exists(root.resolve("skoruba4j-sts"))) {
        root = root.getParent();
      }
    }
    Path yaml = root.resolve("skoruba4j-sts").resolve("application-local.yml");
    if (!Files.exists(yaml)) {
      yaml = root.resolve("skoruba4j-sts").resolve("application-elcss.yml");
    }
    if (!Files.exists(yaml)) {
      yaml =
          root.resolve("dist")
              .resolve("tomcat")
              .resolve("skoruba4j-sts")
              .resolve("application-local.yml");
    }
    if (!Files.exists(yaml)) {
      yaml =
          root.resolve("dist")
              .resolve("tomcat")
              .resolve("skoruba4j-sts")
              .resolve("application-elcss.yml");
    }
    String text = Files.readString(yaml, StandardCharsets.UTF_8);
    String url = yamlValue(text, "url");
    String username = yamlValue(text, "username");
    String password = yamlValue(text, "password");
    if (url.isBlank()) {
      throw new IllegalStateException("SQL Server url missing in " + yaml.getFileName());
    }
    Path destFile;
    if (args.length > 0 && !args[0].isBlank()) {
      destFile = Path.of(args[0]).toAbsolutePath().normalize();
    } else if (Files.isDirectory(root.resolve("dist"))) {
      destFile = SqlitePaths.file(root.resolve("dist"));
    } else {
      destFile = SqlitePaths.file(root);
    }
    Files.createDirectories(destFile.getParent());
    Files.deleteIfExists(destFile);
    Files.deleteIfExists(Path.of(destFile.toString() + "-wal"));
    Files.deleteIfExists(Path.of(destFile.toString() + "-shm"));
    HikariDataSource sqlServer = new HikariDataSource();
    sqlServer.setJdbcUrl(url);
    sqlServer.setUsername(username);
    sqlServer.setPassword(password);
    sqlServer.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    sqlServer.setMaximumPoolSize(2);
    sqlServer.setPoolName("copy-sqlserver");
    SQLiteDataSource sqlite = new SQLiteDataSource();
    sqlite.setUrl(SqlitePaths.jdbcUrl(destFile));
    try {
      Map<String, Integer> counts = SqlServerToSqlite.copy(sqlServer, sqlite, TableStyle.SKORUBA);
      System.out.println("sqlite=" + destFile);
      counts.forEach((table, n) -> System.out.println(table + "=" + n));
    } finally {
      sqlServer.close();
    }
  }

  static String yamlValue(String yaml, String key) {
    String prefix = key + ":";
    for (String line : yaml.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith(prefix)) {
        String value = trimmed.substring(prefix.length()).trim();
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
          return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
          return value.substring(1, value.length() - 1);
        }
        return value;
      }
    }
    return "";
  }
}
