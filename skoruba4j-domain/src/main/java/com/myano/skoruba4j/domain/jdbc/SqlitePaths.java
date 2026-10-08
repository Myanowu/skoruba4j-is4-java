package com.myano.skoruba4j.domain.jdbc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/** File location and JDBC URL for the bundled first-install SQLite database. */
public final class SqlitePaths {
  public static final String FILE_NAME = "skoruba4j.sqlite";
  public static final String RELATIVE_FILE = "data/" + FILE_NAME;
  public static final String QUERY = "?journal_mode=WAL&busy_timeout=8000";
  /** Portable URL stored in yaml; resolved against IDSERVER_HOME at process start. */
  public static final String PORTABLE_JDBC_URL =
      "jdbc:sqlite:${IDSERVER_HOME}/" + RELATIVE_FILE + QUERY;

  /** Result of {@link #createEmptyFile(Path)}. Never overwrites an existing file. */
  public enum CreateEmptyResult {
    CREATED,
    ALREADY_EXISTS
  }

  private SqlitePaths() {}

  public static Path installHome() {
    String home = System.getProperty("IDSERVER_HOME");
    if (home == null || home.isBlank()) {
      home = System.getenv("IDSERVER_HOME");
    }
    if (home == null || home.isBlank()) {
      return Path.of(".").toAbsolutePath().normalize();
    }
    return Path.of(home.trim()).toAbsolutePath().normalize();
  }

  public static Path file(Path installHome) {
    Path home = installHome == null ? Path.of(".") : installHome;
    return home.toAbsolutePath().normalize().resolve("data").resolve(FILE_NAME);
  }

  public static String jdbcUrl(Path dbFile) {
    return "jdbc:sqlite:" + abs(dbFile) + QUERY;
  }

  public static String resolveJdbcUrl(String url) {
    return resolveJdbcUrl(url, installHome());
  }

  public static String resolveJdbcUrl(String url, Path installHome) {
    if (url == null || url.isBlank() || !url.startsWith("jdbc:sqlite:")) {
      return url;
    }
    Path home = installHome == null ? installHome() : installHome.toAbsolutePath().normalize();
    String rest = url.substring("jdbc:sqlite:".length()).trim();
    String query = QUERY;
    int q = rest.indexOf('?');
    if (q >= 0) {
      query = rest.substring(q);
      rest = rest.substring(0, q);
    }
    String homeAbs = abs(home);
    rest =
        rest.replace("${IDSERVER_HOME}", homeAbs)
            .replace("${idserver.home}", homeAbs)
            .replace('\\', '/');
    Path file = Path.of(rest);
    if (!file.isAbsolute()) {
      file = home.resolve(rest);
    }
    return "jdbc:sqlite:" + abs(file) + query;
  }

  public static void ensureParent(String jdbcUrl, Path installHome) {
    String resolved = resolveJdbcUrl(jdbcUrl, installHome);
    if (resolved == null || !resolved.startsWith("jdbc:sqlite:")) {
      return;
    }
    String rest = resolved.substring("jdbc:sqlite:".length());
    int query = rest.indexOf('?');
    if (query >= 0) {
      rest = rest.substring(0, query);
    }
    Path db = Path.of(rest);
    try {
      if (db.getParent() != null) {
        Files.createDirectories(db.getParent());
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  /**
   * Resolve a DB-file field (absolute path, relative path, or {@code ${IDSERVER_HOME}/…}) to an
   * absolute path under {@code installHome}.
   */
  public static Path resolveDbFile(String fileField, Path installHome) {
    Path home = installHome == null ? Path.of(".") : installHome.toAbsolutePath().normalize();
    String file = fileField == null ? "" : fileField.trim();
    if (file.isBlank()) {
      return file(home);
    }
    String homeAbs = abs(home);
    String rest =
        file.replace("${IDSERVER_HOME}", homeAbs)
            .replace("${idserver.home}", homeAbs)
            .replace('\\', '/');
    Path db = Path.of(rest);
    if (!db.isAbsolute()) {
      db = home.resolve(rest);
    }
    return db.toAbsolutePath().normalize();
  }

  /** Append {@code .sqlite} when the path has no {@code .sqlite}/{@code .db} suffix. */
  public static Path ensureSqliteSuffix(Path path) {
    if (path == null || path.getFileName() == null) {
      return path;
    }
    String name = path.getFileName().toString();
    String lower = name.toLowerCase();
    if (lower.endsWith(".sqlite") || lower.endsWith(".db")) {
      return path;
    }
    return path.resolveSibling(name + ".sqlite");
  }

  /**
   * Create an empty SQLite database file (no schema). If the file already exists, leave it
   * untouched and return {@link CreateEmptyResult#ALREADY_EXISTS}.
   */
  public static CreateEmptyResult createEmptyFile(Path dbFile) throws IOException {
    if (dbFile == null) {
      throw new IllegalArgumentException("dbFile is required");
    }
    Path file = ensureSqliteSuffix(dbFile.toAbsolutePath().normalize());
    if (Files.exists(file)) {
      return CreateEmptyResult.ALREADY_EXISTS;
    }
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      throw new IOException("SQLite JDBC driver not on classpath", e);
    }
    String url = "jdbc:sqlite:" + abs(file);
    try (Connection connection = DriverManager.getConnection(url);
        Statement st = connection.createStatement()) {
      st.execute("PRAGMA journal_mode=WAL");
    } catch (Exception e) {
      throw new IOException("Failed to create SQLite file: " + file + " — " + e.getMessage(), e);
    }
    if (!Files.isRegularFile(file)) {
      throw new IOException("SQLite file was not created: " + file);
    }
    return CreateEmptyResult.CREATED;
  }

  private static String abs(Path path) {
    return path.toAbsolutePath().normalize().toString().replace('\\', '/');
  }
}
