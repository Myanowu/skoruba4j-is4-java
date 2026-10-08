package com.myano.skoruba4j.domain.jdbc;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Emergency single-row load/update for identity catalog tables. Not a general ORM — primary keys
 * come from JDBC metadata (or {@code Id} fallback). PasswordHash accepts a plain new password and
 * writes ASP.NET Identity v3.
 */
public final class IdentityStoreRowEditor {
  private IdentityStoreRowEditor() {}

  public record ColumnInfo(String name, boolean primaryKey, boolean sensitive, int sqlType) {}

  public record RowModel(String table, List<ColumnInfo> columns, Map<String, String> values) {
    public List<String> primaryKeyNames() {
      List<String> keys = new ArrayList<>();
      for (ColumnInfo c : columns) {
        if (c.primaryKey()) {
          keys.add(c.name());
        }
      }
      return keys;
    }
  }

  public static RowModel load(
      DataSource dataSource,
      DbProvider provider,
      TableStyle style,
      String table,
      Map<String, String> primaryKeyValues)
      throws SQLException {
    String name = requireCatalogTable(style, table);
    if (primaryKeyValues == null || primaryKeyValues.isEmpty()) {
      throw new IllegalArgumentException("Primary key values are required.");
    }
    SqlDialect dialect = new SqlDialect(provider);
    try (Connection connection = dataSource.getConnection()) {
      List<ColumnInfo> columns = describe(connection, dialect, name);
      List<String> pkNames = pkNames(columns);
      if (pkNames.isEmpty()) {
        throw new IllegalArgumentException("No primary key for table " + name);
      }
      StringBuilder sql = new StringBuilder("SELECT * FROM ").append(dialect.quote(name)).append(" WHERE ");
      List<Object> args = new ArrayList<>();
      for (int i = 0; i < pkNames.size(); i++) {
        if (i > 0) {
          sql.append(" AND ");
        }
        String pk = pkNames.get(i);
        sql.append(dialect.quote(pk)).append(" = ?");
        args.add(primaryKeyValues.get(pk));
        if (args.get(i) == null) {
          // try case-insensitive key match from UI map
          args.set(i, valueIgnoreCase(primaryKeyValues, pk));
        }
        if (args.get(i) == null) {
          throw new IllegalArgumentException("Missing primary key value: " + pk);
        }
      }
      try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
        for (int i = 0; i < args.size(); i++) {
          ps.setObject(i + 1, args.get(i));
        }
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) {
            throw new IllegalArgumentException("Row not found.");
          }
          Map<String, String> values = new LinkedHashMap<>();
          for (ColumnInfo column : columns) {
            values.put(
                column.name(), IdentityStoreAdmin.cellText(rs.getObject(column.name()), false));
          }
          return new RowModel(name, columns, values);
        }
      }
    }
  }

  /**
   * Apply updates. Sensitive columns left blank (or unchanged) are skipped. For {@code PasswordHash},
   * {@code newPassword} if non-blank is hashed and written instead of a raw hash paste.
   *
   * @return number of columns updated (0 if nothing changed)
   */
  public static int update(
      DataSource dataSource,
      DbProvider provider,
      TableStyle style,
      RowModel original,
      Map<String, String> proposed,
      String newPassword)
      throws SQLException {
    if (original == null) {
      throw new IllegalArgumentException("original row is required");
    }
    String name = requireCatalogTable(style, original.table());
    SqlDialect dialect = new SqlDialect(provider);
    Map<String, String> next = proposed == null ? Map.of() : proposed;
    List<String> sets = new ArrayList<>();
    List<Object> args = new ArrayList<>();
    for (ColumnInfo column : original.columns()) {
      if (column.primaryKey()) {
        continue;
      }
      if ("PasswordHash".equalsIgnoreCase(column.name())) {
        if (newPassword != null && !newPassword.isBlank()) {
          sets.add(dialect.quote(column.name()) + " = ?");
          args.add(new IdentityPasswordHasher().hash(newPassword));
        }
        continue;
      }
      if (column.sensitive()) {
        String incoming = valueIgnoreCase(next, column.name());
        if (incoming == null || incoming.isBlank() || "••••••••".equals(incoming)) {
          continue;
        }
        sets.add(dialect.quote(column.name()) + " = ?");
        args.add(coerce(incoming, column.sqlType()));
        continue;
      }
      String incoming = valueIgnoreCase(next, column.name());
      if (incoming == null) {
        continue;
      }
      String previous = original.values().get(column.name());
      if (Objects.equals(nullToEmpty(previous), incoming)) {
        continue;
      }
      sets.add(dialect.quote(column.name()) + " = ?");
      args.add(coerce(incoming, column.sqlType()));
    }
    if (sets.isEmpty()) {
      return 0;
    }
    StringBuilder sql = new StringBuilder("UPDATE ").append(dialect.quote(name)).append(" SET ");
    sql.append(String.join(", ", sets)).append(" WHERE ");
    List<String> pkNames = pkNames(original.columns());
    for (int i = 0; i < pkNames.size(); i++) {
      if (i > 0) {
        sql.append(" AND ");
      }
      String pk = pkNames.get(i);
      sql.append(dialect.quote(pk)).append(" = ?");
      args.add(original.values().get(pk));
    }
    return Jdbc.execute(dataSource, sql.toString(), args.toArray());
  }

  public static List<ColumnInfo> describe(
      Connection connection, SqlDialect dialect, String table) throws SQLException {
    List<String> names = IdentityStoreAdmin.columnNames(connection, dialect, table);
    List<String> pks = readPrimaryKeys(connection, table);
    if (pks.isEmpty()) {
      for (String name : names) {
        if ("Id".equalsIgnoreCase(name)) {
          pks = List.of(name);
          break;
        }
      }
    }
    // Align PK names to actual column casing
    List<String> resolvedPk = new ArrayList<>();
    for (String pk : pks) {
      for (String name : names) {
        if (name.equalsIgnoreCase(pk)) {
          resolvedPk.add(name);
          break;
        }
      }
    }
    Map<String, Integer> types = columnTypes(connection, dialect, table);
    List<ColumnInfo> columns = new ArrayList<>(names.size());
    for (String name : names) {
      boolean pk = false;
      for (String key : resolvedPk) {
        if (key.equalsIgnoreCase(name)) {
          pk = true;
          break;
        }
      }
      columns.add(
          new ColumnInfo(
              name,
              pk,
              IdentityStoreAdmin.isSensitiveColumn(table, name),
              types.getOrDefault(name, Types.VARCHAR)));
    }
    return columns;
  }

  private static Map<String, Integer> columnTypes(
      Connection connection, SqlDialect dialect, String table) throws SQLException {
    Map<String, Integer> types = new LinkedHashMap<>();
    String sql = "SELECT * FROM " + dialect.quote(table) + " WHERE 1=0";
    try (PreparedStatement ps = connection.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      ResultSetMetaData meta = rs.getMetaData();
      for (int i = 1; i <= meta.getColumnCount(); i++) {
        String label = meta.getColumnLabel(i);
        if (label == null || label.isBlank()) {
          label = meta.getColumnName(i);
        }
        types.put(label, meta.getColumnType(i));
      }
    }
    return types;
  }

  private static List<String> readPrimaryKeys(Connection connection, String table)
      throws SQLException {
    DatabaseMetaData meta = connection.getMetaData();
    List<String> keys = new ArrayList<>();
    try (ResultSet rs = meta.getPrimaryKeys(connection.getCatalog(), null, table)) {
      while (rs.next()) {
        keys.add(rs.getString("COLUMN_NAME"));
      }
    }
    if (keys.isEmpty()) {
      try (ResultSet rs = meta.getPrimaryKeys(null, null, table)) {
        while (rs.next()) {
          keys.add(rs.getString("COLUMN_NAME"));
        }
      }
    }
    // SQL Server sometimes needs dbo schema
    if (keys.isEmpty()) {
      try (ResultSet rs = meta.getPrimaryKeys(connection.getCatalog(), "dbo", table)) {
        while (rs.next()) {
          keys.add(rs.getString("COLUMN_NAME"));
        }
      }
    }
    return keys;
  }

  private static List<String> pkNames(List<ColumnInfo> columns) {
    List<String> keys = new ArrayList<>();
    for (ColumnInfo c : columns) {
      if (c.primaryKey()) {
        keys.add(c.name());
      }
    }
    return keys;
  }

  private static String requireCatalogTable(TableStyle style, String table) {
    String name = table == null ? "" : table.trim();
    for (String expected : IdentityStoreAdmin.expectedTables(style)) {
      if (expected.equalsIgnoreCase(name)) {
        return expected;
      }
    }
    throw new IllegalArgumentException("Table is not in the identity catalog.");
  }

  private static String valueIgnoreCase(Map<String, String> map, String key) {
    if (map == null || key == null) {
      return null;
    }
    if (map.containsKey(key)) {
      return map.get(key);
    }
    for (Map.Entry<String, String> e : map.entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
        return e.getValue();
      }
    }
    return null;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static Object coerce(String value, int sqlType) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return switch (sqlType) {
      case Types.BOOLEAN, Types.BIT ->
          "1".equals(trimmed)
              || "true".equalsIgnoreCase(trimmed)
              || "yes".equalsIgnoreCase(trimmed);
      case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
        if (trimmed.isBlank()) {
          yield null;
        }
        yield Integer.parseInt(trimmed);
      }
      case Types.BIGINT -> {
        if (trimmed.isBlank()) {
          yield null;
        }
        yield Long.parseLong(trimmed);
      }
      default -> value;
    };
  }
}
