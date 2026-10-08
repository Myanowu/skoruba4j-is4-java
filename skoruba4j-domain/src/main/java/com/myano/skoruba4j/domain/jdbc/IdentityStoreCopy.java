package com.myano.skoruba4j.domain.jdbc;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.IdentityTables;
import com.myano.skoruba4j.domain.TableStyle;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
import org.sqlite.SQLiteDataSource;

/**
 * Copy IS4 / Identity rows. Backup always lands in SQLite. Restore can target sqlite, SQL Server,
 * PostgreSQL, or MySQL. Counts only; no secrets logged.
 */
public final class IdentityStoreCopy {
  private IdentityStoreCopy() {}

  public static DataSource sqliteFile(Path file) {
    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl(SqlitePaths.jdbcUrl(file));
    return ds;
  }

  public static Map<String, Integer> backupToFile(
      DataSource source, Path sqliteFile, TableStyle style) {
    try {
      if (sqliteFile.getParent() != null) {
        Files.createDirectories(sqliteFile.getParent());
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
    return backupToSqlite(source, sqliteFile(sqliteFile), style);
  }

  public static Map<String, Integer> backupToSqlite(
      DataSource source, DataSource sqlite, TableStyle style) {
    SqliteSchema.createEmpty(sqlite, style);
    SqlServerToSqlite.clearSqlite(sqlite, style);
    return copyTables(source, sqlite, DbProvider.SQLITE, style);
  }

  public static Map<String, Integer> restoreFromFile(
      Path sqliteFile, DataSource dest, DbProvider destProvider, TableStyle style) {
    return restoreFromSqlite(sqliteFile(sqliteFile), dest, destProvider, style);
  }

  public static Map<String, Integer> restoreFromSqlite(
      DataSource sqlite, DataSource dest, DbProvider destProvider, TableStyle style) {
    if (destProvider == DbProvider.SQLITE) {
      SqliteSchema.createEmpty(dest, style);
      SqlServerToSqlite.clearSqlite(dest, style);
    } else {
      clearDest(dest, destProvider, style);
    }
    return copyTables(sqlite, dest, destProvider, style);
  }

  static Map<String, Integer> copyTables(
      DataSource source, DataSource dest, DbProvider destProvider, TableStyle style) {
    List<String> tables = SqlServerToSqlite.tableOrder(IdentityTables.forStyle(style));
    Map<String, Integer> counts = new LinkedHashMap<>();
    try (Connection src = source.getConnection();
        Connection dst = dest.getConnection()) {
      dst.setAutoCommit(false);
      disableChecks(dst, destProvider);
      for (String table : tables) {
        counts.put(table, copyTable(src, dst, destProvider, table));
      }
      dst.commit();
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
    return counts;
  }

  static void clearDest(DataSource dest, DbProvider provider, TableStyle style) {
    List<String> tables = SqlServerToSqlite.tableOrder(IdentityTables.forStyle(style));
    SqlDialect dialect = new SqlDialect(provider);
    try (Connection connection = dest.getConnection();
        Statement st = connection.createStatement()) {
      disableChecks(connection, provider);
      for (int i = tables.size() - 1; i >= 0; i--) {
        String table = tables.get(i);
        try {
          if (IdentityStoreAdmin.tablePresent(connection, table)) {
            st.executeUpdate("DELETE FROM " + dialect.quote(table));
          }
        } catch (SQLException ignored) {
          // missing table on the target
        }
      }
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  private static int copyTable(
      Connection source, Connection dest, DbProvider destProvider, String table) throws SQLException {
    if (!IdentityStoreAdmin.tablePresent(source, table)
        || !IdentityStoreAdmin.tablePresent(dest, table)) {
      return -1;
    }
    List<Column> destCols = columns(dest, destProvider, table);
    if (destCols.isEmpty()) {
      return -1;
    }
    SqlDialect sourceDialect = dialectOf(source);
    SqlDialect destDialect = new SqlDialect(destProvider);
    String select = "SELECT * FROM " + sourceDialect.quote(table);
    String insert =
        "INSERT INTO "
            + destDialect.quote(table)
            + " ("
            + String.join(", ", destCols.stream().map(c -> destDialect.quote(c.name())).toList())
            + ") VALUES ("
            + String.join(", ", destCols.stream().map(c -> "?").toList())
            + ")";
    boolean identity = destProvider == DbProvider.SQLSERVER && hasIdentity(destCols);
    try (Statement st = source.createStatement();
        ResultSet rs = st.executeQuery(select);
        Statement ident = dest.createStatement()) {
      if (identity) {
        ident.execute("SET IDENTITY_INSERT " + destDialect.quote(table) + " ON");
      }
      try (PreparedStatement ps = dest.prepareStatement(insert)) {
        st.setFetchSize(200);
        ResultSetMetaData meta = rs.getMetaData();
        int[] sourceIndex = new int[destCols.size()];
        int[] sourceType = new int[destCols.size()];
        for (int c = 0; c < destCols.size(); c++) {
          sourceIndex[c] = findColumn(meta, destCols.get(c).name());
          sourceType[c] = sourceIndex[c] == 0 ? Types.NULL : meta.getColumnType(sourceIndex[c]);
        }
        int copied = 0;
        while (rs.next()) {
          for (int i = 0; i < destCols.size(); i++) {
            Object raw = sourceIndex[i] == 0 ? null : rs.getObject(sourceIndex[i]);
            ps.setObject(i + 1, convert(raw, sourceType[i], destCols.get(i), destProvider));
          }
          ps.addBatch();
          copied++;
          if (copied % 200 == 0) {
            ps.executeBatch();
          }
        }
        ps.executeBatch();
        if (destProvider == DbProvider.SQLITE) {
          bumpSqliteSequence(dest, table);
        }
        return copied;
      } finally {
        if (identity) {
          try {
            ident.execute("SET IDENTITY_INSERT " + destDialect.quote(table) + " OFF");
          } catch (SQLException ignored) {
            // leave the session usable
          }
        }
      }
    }
  }

  private static void disableChecks(Connection connection, DbProvider provider) throws SQLException {
    try (Statement st = connection.createStatement()) {
      switch (provider) {
        case SQLITE -> {
          st.execute("PRAGMA foreign_keys=OFF");
          st.execute("PRAGMA busy_timeout=8000");
        }
        case MYSQL -> st.execute("SET FOREIGN_KEY_CHECKS=0");
        case POSTGRESQL -> st.execute("SET session_replication_role = replica");
        case SQLSERVER -> {
          // parent-first insert / child-first delete
        }
      }
    } catch (SQLException ignored) {
      // driver or permission may refuse session flags
    }
  }

  private static List<Column> columns(Connection connection, DbProvider provider, String table)
      throws SQLException {
    if (provider == DbProvider.SQLITE) {
      return sqliteColumns(connection, table);
    }
    List<Column> cols = new ArrayList<>();
    DatabaseMetaData meta = connection.getMetaData();
    try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table, null)) {
      while (rs.next()) {
        String name = rs.getString("COLUMN_NAME");
        String type = rs.getString("TYPE_NAME");
        boolean notNull = rs.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
        boolean auto = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
        cols.add(new Column(name, type, notNull, auto));
      }
    }
    if (cols.isEmpty()) {
      try (ResultSet rs = meta.getColumns(null, null, table, null)) {
        while (rs.next()) {
          String name = rs.getString("COLUMN_NAME");
          String type = rs.getString("TYPE_NAME");
          boolean notNull = rs.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
          boolean auto = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
          cols.add(new Column(name, type, notNull, auto));
        }
      }
    }
    return cols;
  }

  private static List<Column> sqliteColumns(Connection dest, String table) throws SQLException {
    List<Column> cols = new ArrayList<>();
    try (Statement st = dest.createStatement();
        ResultSet rs = st.executeQuery("PRAGMA table_info(" + quote(DbProvider.SQLITE, table) + ")")) {
      while (rs.next()) {
        String type = rs.getString("type");
        boolean auto =
            rs.getInt("pk") == 1 && type != null && type.toUpperCase(Locale.ROOT).contains("INT");
        cols.add(new Column(rs.getString("name"), type, rs.getInt("notnull") == 1, auto));
      }
    }
    return cols;
  }

  private static boolean hasIdentity(List<Column> cols) {
    for (Column col : cols) {
      if (col.auto()) {
        return true;
      }
    }
    return false;
  }

  private static Object convert(Object value, int jdbcType, Column column, DbProvider dest) {
    Object converted = normalize(value, jdbcType);
    if (dest == DbProvider.SQLITE) {
      return sqliteNotNull(converted, column);
    }
    if (converted != null) {
      return converted;
    }
    if (!column.notNull()) {
      return null;
    }
    String type = column.type() == null ? "" : column.type().toUpperCase(Locale.ROOT);
    if (type.contains("INT") || type.contains("BOOL") || type.contains("BIT") || type.contains("NUM")) {
      return 0;
    }
    if (type.contains("DATE") || type.contains("TIME")) {
      return Timestamp.valueOf("1970-01-01 00:00:00");
    }
    return "";
  }

  private static Object sqliteNotNull(Object converted, Column column) {
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

  private static Object normalize(Object value, int jdbcType) {
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
      return Timestamp.from(odt.toInstant());
    }
    if (value instanceof java.util.Date date && !(value instanceof Timestamp)) {
      return new Timestamp(date.getTime());
    }
    if (value instanceof byte[] || value instanceof Number || value instanceof String || value instanceof Timestamp) {
      return value;
    }
    if (value instanceof BigDecimal dec) {
      return dec.toPlainString();
    }
    return value.toString();
  }

  private static void bumpSqliteSequence(Connection dest, String table) {
    try (Statement st = dest.createStatement();
        ResultSet pk = st.executeQuery("PRAGMA table_info(" + quote(DbProvider.SQLITE, table) + ")")) {
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
                  "SELECT MAX(" + quote(DbProvider.SQLITE, pkCol) + ") FROM " + quote(DbProvider.SQLITE, table))) {
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

  private static SqlDialect dialectOf(Connection connection) throws SQLException {
    String url = connection.getMetaData().getURL();
    if (url != null && url.startsWith("jdbc:sqlite:")) {
      return new SqlDialect(DbProvider.SQLITE);
    }
    if (url != null && url.startsWith("jdbc:sqlserver:")) {
      return new SqlDialect(DbProvider.SQLSERVER);
    }
    if (url != null && (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:pgsql:"))) {
      return new SqlDialect(DbProvider.POSTGRESQL);
    }
    if (url != null && url.startsWith("jdbc:mysql:")) {
      return new SqlDialect(DbProvider.MYSQL);
    }
    return new SqlDialect(DbProvider.SQLITE);
  }

  private static int findColumn(ResultSetMetaData meta, String name) throws SQLException {
    for (int i = 1; i <= meta.getColumnCount(); i++) {
      if (name.equalsIgnoreCase(meta.getColumnLabel(i)) || name.equalsIgnoreCase(meta.getColumnName(i))) {
        return i;
      }
    }
    return 0;
  }

  private static String quote(DbProvider provider, String identifier) {
    return new SqlDialect(provider).quote(identifier);
  }

  private record Column(String name, String type, boolean notNull, boolean auto) {}
}
