package com.myano.skoruba4j.domain.jdbc;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.IdentityTables;
import com.myano.skoruba4j.domain.TableStyle;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;

/** Inspect / initialize the shared IS4 + Identity store. Not Flyway. */
public final class IdentityStoreAdmin {
  private static final int PREVIEW_CELL_MAX = 240;

  private IdentityStoreAdmin() {}

  public record TableStatus(String name, boolean present, int rows) {}

  public record Preview(
      String table,
      int limit,
      int offset,
      String orderBy,
      boolean ascending,
      boolean secretsRevealed,
      List<String> columns,
      List<Boolean> sensitiveColumns,
      List<List<String>> rows,
      String error) {
    public static Preview fail(String table, int limit, int offset, String error) {
      return new Preview(
          table,
          limit,
          offset,
          "",
          true,
          false,
          List.of(),
          List.of(),
          List.of(),
          error == null ? "" : error);
    }
  }

  public record Report(boolean ping, String error, List<TableStatus> tables, boolean demoSeeded) {
    public Report(boolean ping, String error, List<TableStatus> tables) {
      this(ping, error, tables, false);
    }

    public int expected() {
      return tables == null ? 0 : tables.size();
    }

    public int presentCount() {
      if (tables == null) {
        return 0;
      }
      int n = 0;
      for (TableStatus row : tables) {
        if (row.present()) {
          n++;
        }
      }
      return n;
    }

    public String summary() {
      if (error != null && !error.isBlank()) {
        return error;
      }
      String base = "Connected. " + presentCount() + " / " + expected() + " expected tables.";
      if (demoSeeded) {
        return base
            + " DEMO seeded: "
            + SqliteSchema.DEMO_USERNAME
            + " / "
            + SqliteSchema.DEMO_PASSWORD
            + " (role "
            + SqliteSchema.DEMO_ROLE
            + ").";
      }
      return base;
    }
  }

  public static List<String> expectedTables(TableStyle style) {
    return List.copyOf(SqlServerToSqlite.tableOrder(IdentityTables.forStyle(style)));
  }

  /**
   * Read up to {@code limit} rows from an expected identity table. Table name must be in the
   * expected catalog for {@code style} (prevents arbitrary SQL identifiers from the UI).
   */
  public static Preview preview(
      DataSource dataSource, DbProvider provider, TableStyle style, String table, int limit) {
    return preview(dataSource, provider, style, table, 0, limit, null, true, false);
  }

  public static Preview preview(
      DataSource dataSource,
      DbProvider provider,
      TableStyle style,
      String table,
      int offset,
      int limit,
      String orderBy,
      boolean revealSecrets) {
    return preview(dataSource, provider, style, table, offset, limit, orderBy, true, revealSecrets);
  }

  /**
   * Paged preview. {@code orderBy} must match a real column (or null to pick Id / first column).
   * Sensitive columns are masked unless {@code revealSecrets} is true.
   */
  public static Preview preview(
      DataSource dataSource,
      DbProvider provider,
      TableStyle style,
      String table,
      int offset,
      int limit,
      String orderBy,
      boolean ascending,
      boolean revealSecrets) {
    if (dataSource == null) {
      return Preview.fail(table, limit, offset, "No data source.");
    }
    String name = table == null ? "" : table.trim();
    if (name.isBlank()) {
      return Preview.fail(name, limit, offset, "No table selected.");
    }
    Set<String> allowed = Set.copyOf(expectedTables(style));
    boolean ok = false;
    for (String expected : allowed) {
      if (expected.equalsIgnoreCase(name)) {
        name = expected;
        ok = true;
        break;
      }
    }
    if (!ok) {
      return Preview.fail(name, limit, offset, "Table is not in the identity catalog.");
    }
    int n = Math.max(1, Math.min(limit, 10_000));
    int off = Math.max(0, offset);
    SqlDialect dialect = new SqlDialect(provider);
    try (Connection connection = dataSource.getConnection()) {
      List<String> columns = columnNames(connection, dialect, name);
      if (columns.isEmpty()) {
        return Preview.fail(name, n, off, "Could not read columns.");
      }
      String orderCol = resolveOrderBy(columns, orderBy);
      List<Boolean> sensitive = new ArrayList<>(columns.size());
      for (String column : columns) {
        sensitive.add(isSensitiveColumn(name, column));
      }
      String sql =
          dialect.selectPaged(
              "*",
              "FROM " + dialect.quote(name),
              dialect.quote(orderCol),
              ascending,
              off,
              n);
      try (PreparedStatement ps = connection.prepareStatement(sql);
          ResultSet rs = ps.executeQuery()) {
        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
          List<String> row = new ArrayList<>(columns.size());
          for (int i = 0; i < columns.size(); i++) {
            boolean mask = Boolean.TRUE.equals(sensitive.get(i)) && !revealSecrets;
            row.add(mask ? "••••••••" : cellText(rs.getObject(i + 1)));
          }
          rows.add(List.copyOf(row));
        }
        return new Preview(
            name,
            n,
            off,
            orderCol,
            ascending,
            revealSecrets,
            List.copyOf(columns),
            List.copyOf(sensitive),
            List.copyOf(rows),
            "");
      }
    } catch (SQLException e) {
      return Preview.fail(name, n, off, message(e));
    }
  }

  static List<String> columnNames(Connection connection, SqlDialect dialect, String table)
      throws SQLException {
    String sql = "SELECT * FROM " + dialect.quote(table) + " WHERE 1=0";
    try (PreparedStatement ps = connection.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      ResultSetMetaData meta = rs.getMetaData();
      int cols = meta.getColumnCount();
      List<String> columns = new ArrayList<>(cols);
      for (int i = 1; i <= cols; i++) {
        String label = meta.getColumnLabel(i);
        if (label == null || label.isBlank()) {
          label = meta.getColumnName(i);
        }
        columns.add(label == null ? "c" + i : label);
      }
      return columns;
    }
  }

  static String resolveOrderBy(List<String> columns, String requested) {
    if (requested != null && !requested.isBlank()) {
      for (String column : columns) {
        if (column.equalsIgnoreCase(requested.trim())) {
          return column;
        }
      }
    }
    for (String prefer : new String[] {"Id", "ID", "UserId", "ClientId", "Key"}) {
      for (String column : columns) {
        if (column.equalsIgnoreCase(prefer)) {
          return column;
        }
      }
    }
    return columns.get(0);
  }

  static boolean isSensitiveColumn(String table, String column) {
    if (column == null || column.isBlank()) {
      return false;
    }
    String c = column.toLowerCase(Locale.ROOT);
    if (c.contains("password") || c.contains("secret") || "securitystamp".equals(c)) {
      return true;
    }
    String t = table == null ? "" : table.toLowerCase(Locale.ROOT);
    if ("clientsecrets".equals(t) && "value".equals(c)) {
      return true;
    }
    if ("persistedgrants".equals(t) && ("data".equals(c) || "key".equals(c))) {
      return true;
    }
    if (("usertokens".equals(t) || "devicecodes".equals(t))
        && ("value".equals(c) || "data".equals(c))) {
      return true;
    }
    return false;
  }

  public static Report inspect(DataSource dataSource, DbProvider provider, TableStyle style) {
    try {
      if (!Jdbc.ping(dataSource)) {
        return new Report(false, "Ping failed.", List.of());
      }
    } catch (RuntimeException e) {
      return new Report(false, message(e), List.of());
    }
    SqlDialect dialect = new SqlDialect(provider);
    List<TableStatus> rows = new ArrayList<>();
    try (Connection connection = dataSource.getConnection()) {
      for (String table : expectedTables(style)) {
        boolean present = tablePresent(connection, table);
        int count = 0;
        if (present) {
          try {
            count = Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + dialect.quote(table));
          } catch (RuntimeException e) {
            count = -1;
          }
        }
        rows.add(new TableStatus(table, present, present ? count : -1));
      }
    } catch (SQLException e) {
      return new Report(false, message(e), List.of());
    }
    return new Report(true, "", List.copyOf(rows));
  }

  /** SQLite only: CREATE IF NOT EXISTS, then seed demo user if Users is empty. */
  public static Report initializeSqlite(DataSource dataSource, TableStyle style) {
    boolean seeded = SqliteSchema.ensure(dataSource, style);
    Report inspected = inspect(dataSource, DbProvider.SQLITE, style);
    return new Report(inspected.ping(), inspected.error(), inspected.tables(), seeded);
  }

  public static Report initializeSqlite(
      DataSource dataSource, TableStyle style, String jdbcUrl, Path installHome) {
    SqlitePaths.ensureParent(jdbcUrl, installHome);
    return initializeSqlite(dataSource, style);
  }

  static boolean tablePresent(Connection connection, String table) throws SQLException {
    DatabaseMetaData meta = connection.getMetaData();
    try (ResultSet rs = meta.getTables(connection.getCatalog(), null, table, new String[] {"TABLE"})) {
      while (rs.next()) {
        String name = rs.getString("TABLE_NAME");
        if (table.equalsIgnoreCase(name)) {
          return true;
        }
      }
    }
    try (ResultSet rs = meta.getTables(null, null, table, new String[] {"TABLE"})) {
      while (rs.next()) {
        String name = rs.getString("TABLE_NAME");
        if (table.equalsIgnoreCase(name)) {
          return true;
        }
      }
    }
    return false;
  }

  static String cellText(Object value) {
    return cellText(value, true);
  }

  /** Full string for editors; preview truncates and soft-masks long Identity hashes. */
  static String cellText(Object value, boolean forPreview) {
    if (value == null) {
      return "";
    }
    if (value instanceof byte[] bytes) {
      return "(blob " + bytes.length + " bytes)";
    }
    String text = String.valueOf(value);
    if (!forPreview) {
      return text;
    }
    if (text.length() > PREVIEW_CELL_MAX) {
      return text.substring(0, PREVIEW_CELL_MAX) + "…";
    }
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.startsWith("aqaaaa") && text.length() > 40) {
      return text.substring(0, 12) + "…";
    }
    return text;
  }

  private static String message(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    String text = t.getMessage();
    return text == null || text.isBlank() ? t.getClass().getSimpleName() : text;
  }
}
