package com.myano.skoruba4j.domain.jdbc;

import com.myano.skoruba4j.domain.ConfigurationTables;
import com.myano.skoruba4j.domain.IdentityTables;
import com.myano.skoruba4j.domain.TableStyle;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/** Copy IS4 / Identity rows from SQL Server into an empty SQLite file. Counts only; no secrets logged. */
public final class SqlServerToSqlite {
  private SqlServerToSqlite() {}

  public static Map<String, Integer> copy(DataSource sqlServer, DataSource sqlite, TableStyle style) {
    return IdentityStoreCopy.backupToSqlite(sqlServer, sqlite, style);
  }

  /** Wipe SQLite IS4/Identity rows, then copy from SQL Server. DDL is CREATE IF NOT EXISTS. */
  public static Map<String, Integer> replaceSqlite(
      DataSource sqlServer, DataSource sqlite, TableStyle style) {
    return IdentityStoreCopy.backupToSqlite(sqlServer, sqlite, style);
  }

  static void clearSqlite(DataSource sqlite, TableStyle style) {
    List<String> tables = tableOrder(IdentityTables.forStyle(style));
    try (Connection dest = sqlite.getConnection();
        Statement st = dest.createStatement()) {
      st.execute("PRAGMA foreign_keys=OFF");
      st.execute("PRAGMA busy_timeout=8000");
      for (int i = tables.size() - 1; i >= 0; i--) {
        try {
          st.executeUpdate("DELETE FROM " + sqliteQuote(tables.get(i)));
        } catch (SQLException ignored) {
          // table missing on a half-created file
        }
      }
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  static Map<String, Integer> copyExisting(
      DataSource sqlServer, DataSource sqlite, TableStyle style) {
    List<String> tables = tableOrder(IdentityTables.forStyle(style));
    Map<String, Integer> counts = new LinkedHashMap<>();
    try (Connection source = sqlServer.getConnection();
        Connection dest = sqlite.getConnection()) {
      dest.setAutoCommit(false);
      try (Statement pragma = dest.createStatement()) {
        pragma.execute("PRAGMA foreign_keys=OFF");
        pragma.execute("PRAGMA busy_timeout=8000");
      }
      for (String table : tables) {
        counts.put(table, copyTable(source, dest, table));
      }
      dest.commit();
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
    return counts;
  }

  static List<String> tableOrder(IdentityTables identity) {
    List<String> tables = new ArrayList<>();
    tables.add(identity.users());
    tables.add(identity.roles());
    tables.add(identity.userRoles());
    tables.add(identity.userClaims());
    tables.add(identity.roleClaims());
    tables.add(identity.userLogins());
    tables.add(identity.userTokens());
    tables.add(ConfigurationTables.IDENTITY_RESOURCES);
    tables.add(ConfigurationTables.IDENTITY_RESOURCE_CLAIMS);
    tables.add(ConfigurationTables.IDENTITY_RESOURCE_PROPERTIES);
    tables.add(ConfigurationTables.API_SCOPES);
    tables.add("ApiScopeClaims");
    tables.add(ConfigurationTables.API_SCOPE_PROPERTIES);
    tables.add(ConfigurationTables.API_RESOURCES);
    tables.add(ConfigurationTables.API_RESOURCE_SCOPES);
    tables.add(ConfigurationTables.API_RESOURCE_CLAIMS);
    tables.add(ConfigurationTables.API_RESOURCE_SECRETS);
    tables.add(ConfigurationTables.API_RESOURCE_PROPERTIES);
    tables.add(ConfigurationTables.CLIENTS);
    tables.add(ConfigurationTables.CLIENT_GRANT_TYPES);
    tables.add(ConfigurationTables.CLIENT_REDIRECT_URIS);
    tables.add(ConfigurationTables.CLIENT_POST_LOGOUT_REDIRECT_URIS);
    tables.add(ConfigurationTables.CLIENT_SCOPES);
    tables.add(ConfigurationTables.CLIENT_SECRETS);
    tables.add(ConfigurationTables.CLIENT_CORS_ORIGINS);
    tables.add(ConfigurationTables.CLIENT_CLAIMS);
    tables.add(ConfigurationTables.CLIENT_PROPERTIES);
    tables.add("ClientIdPRestrictions");
    tables.add(ConfigurationTables.PERSISTED_GRANTS);
    tables.add("DeviceCodes");
    return tables;
  }

  private static int copyTable(Connection source, Connection dest, String table) throws SQLException {
    if (!sqlServerTableExists(source, table)) {
      return -1;
    }
    List<Column> cols = sqliteColumns(dest, table);
    if (cols.isEmpty()) {
      return -1;
    }
    String select = "SELECT * FROM " + sqlServerQuote(table);
    String insert =
        "INSERT INTO "
            + sqliteQuote(table)
            + " ("
            + String.join(", ", cols.stream().map(c -> sqliteQuote(c.name())).toList())
            + ") VALUES ("
            + String.join(", ", cols.stream().map(c -> "?").toList())
            + ")";
    int copied = 0;
    try (Statement st = source.createStatement();
        ResultSet rs = st.executeQuery(select);
        PreparedStatement ps = dest.prepareStatement(insert)) {
      st.setFetchSize(200);
      ResultSetMetaData meta = rs.getMetaData();
      int[] sourceIndex = new int[cols.size()];
      int[] sourceType = new int[cols.size()];
      for (int c = 0; c < cols.size(); c++) {
        sourceIndex[c] = findColumn(meta, cols.get(c).name());
        sourceType[c] = sourceIndex[c] == 0 ? Types.NULL : meta.getColumnType(sourceIndex[c]);
      }
      while (rs.next()) {
        for (int i = 0; i < cols.size(); i++) {
          Object raw = sourceIndex[i] == 0 ? null : rs.getObject(sourceIndex[i]);
          ps.setObject(i + 1, sqliteValue(raw, sourceType[i], cols.get(i)));
        }
        ps.addBatch();
        copied++;
        if (copied % 200 == 0) {
          ps.executeBatch();
        }
      }
      ps.executeBatch();
    }
    bumpSqliteSequence(dest, table);
    return copied;
  }

  private static Object sqliteValue(Object value, int jdbcType, Column column) {
    Object converted = sqliteValue(value, jdbcType);
    if (converted != null) {
      return converted;
    }
    if (!column.notNull()) {
      return null;
    }
    String type = column.type() == null ? "" : column.type().toUpperCase(Locale.ROOT);
    if (type.contains("INT") || type.contains("BOOL") || type.contains("BIT")) {
      return 0;
    }
    if ("Created".equalsIgnoreCase(column.name())
        || "Updated".equalsIgnoreCase(column.name())
        || "LastAccessed".equalsIgnoreCase(column.name())) {
      return "1970-01-01 00:00:00";
    }
    return "";
  }

  private static Object sqliteValue(Object value, int jdbcType) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean b) {
      return b ? 1 : 0;
    }
    if (jdbcType == Types.BIT || jdbcType == Types.BOOLEAN) {
      if (value instanceof Number n) {
        return n.intValue() != 0 ? 1 : 0;
      }
    }
    if (value instanceof UUID uuid) {
      return uuid.toString();
    }
    if (value instanceof OffsetDateTime odt) {
      return Timestamp.from(odt.toInstant()).toString();
    }
    if (value instanceof java.util.Date || value instanceof Timestamp) {
      return value.toString();
    }
    if (value instanceof byte[] bytes) {
      return java.util.Base64.getEncoder().encodeToString(bytes);
    }
    if (value instanceof BigDecimal dec) {
      return dec.toPlainString();
    }
    if (value instanceof Number || value instanceof String) {
      return value;
    }
    return value.toString();
  }

  private static void bumpSqliteSequence(Connection dest, String table) {
    try (Statement st = dest.createStatement();
        ResultSet pk = st.executeQuery("PRAGMA table_info(" + sqliteQuote(table) + ")")) {
      String pkCol = null;
      boolean integerPk = false;
      while (pk.next()) {
        if (pk.getInt("pk") == 1) {
          pkCol = pk.getString("name");
          String type = pk.getString("type");
          integerPk = type != null && type.toUpperCase(Locale.ROOT).contains("INT");
        }
      }
      if (pkCol == null || !integerPk) {
        return;
      }
      try (Statement max = dest.createStatement();
          ResultSet rs =
              max.executeQuery(
                  "SELECT MAX(" + sqliteQuote(pkCol) + ") FROM " + sqliteQuote(table))) {
        if (rs.next()) {
          int hi = rs.getInt(1);
          if (!rs.wasNull()) {
            max.executeUpdate(
                "INSERT INTO sqlite_sequence(name, seq) VALUES ('"
                    + table.replace("'", "''")
                    + "', "
                    + hi
                    + ") ON CONFLICT(name) DO UPDATE SET seq=excluded.seq");
          }
        }
      }
    } catch (SQLException ignored) {
      // sqlite_sequence is absent until an AUTOINCREMENT insert happens.
    }
  }

  private static boolean sqlServerTableExists(Connection source, String table) throws SQLException {
    try (PreparedStatement ps =
        source.prepareStatement("SELECT OBJECT_ID(?)")) {
      ps.setString(1, "dbo." + table);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getObject(1) != null;
      }
    }
  }

  private static List<String> sqlServerColumns(Connection source, String table) throws SQLException {
    List<String> cols = new ArrayList<>();
    try (PreparedStatement ps =
        source.prepareStatement(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION")) {
      ps.setString(1, table);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          cols.add(rs.getString(1));
        }
      }
    }
    return cols;
  }

  private static List<Column> sqliteColumns(Connection dest, String table) throws SQLException {
    List<Column> cols = new ArrayList<>();
    try (Statement st = dest.createStatement();
        ResultSet rs = st.executeQuery("PRAGMA table_info(" + sqliteQuote(table) + ")")) {
      while (rs.next()) {
        cols.add(
            new Column(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1));
      }
    }
    return cols;
  }

  private record Column(String name, String type, boolean notNull) {}

  private static int findColumn(ResultSetMetaData meta, String name) throws SQLException {
    for (int i = 1; i <= meta.getColumnCount(); i++) {
      if (name.equalsIgnoreCase(meta.getColumnLabel(i)) || name.equalsIgnoreCase(meta.getColumnName(i))) {
        return i;
      }
    }
    return 0;
  }

  private static String sqlServerQuote(String identifier) {
    return "[" + identifier.replace("]", "]]") + "]";
  }

  private static String sqliteQuote(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }
}
