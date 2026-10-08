package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.ConfigurationTables;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Read/write Skoruba {@code AuditLog} (Admin extras). */
public final class AuditLogRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;

  public AuditLogRepository(DataSource dataSource, SqlDialect dialect) {
    this.dataSource = dataSource;
    this.dialect = dialect;
  }

  public boolean tableReady() {
    try {
      Jdbc.queryForInt(
          dataSource, "SELECT COUNT(*) FROM " + dialect.quote(ConfigurationTables.AUDIT_LOG));
      return true;
    } catch (UncheckedSqlException e) {
      return false;
    }
  }

  public PageResult<AuditLogEntry> search(AuditLogFilter filter) {
    StringBuilder where = new StringBuilder();
    List<Object> args = new ArrayList<>();
    appendLike(where, args, "SubjectIdentifier", filter.subjectIdentifier());
    appendLike(where, args, "SubjectName", filter.subjectName());
    appendLike(where, args, "Event", filter.event());
    appendLike(where, args, "Source", filter.source());
    appendLike(where, args, "Category", filter.category());
    String whereSql = where.length() == 0 ? "" : " WHERE " + where;
    String table = dialect.quote(ConfigurationTables.AUDIT_LOG);
    Object[] argArray = args.toArray();
    int total = Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + table + whereSql, argArray);
    String cols =
        String.join(
            ", ",
            dialect.quote("Id"),
            dialect.quote("Event"),
            dialect.quote("Source"),
            dialect.quote("Category"),
            dialect.quote("SubjectIdentifier"),
            dialect.quote("SubjectName"),
            dialect.quote("SubjectType"),
            dialect.quote("SubjectAdditionalData"),
            dialect.quote("Action"),
            dialect.quote("Data"),
            dialect.quote("Created"));
    String sql =
        dialect.selectPaged(
            cols,
            "FROM " + table + whereSql,
            dialect.quote("Created"),
            false,
            filter.offset(),
            filter.pageSize());
    List<AuditLogEntry> items = Jdbc.query(dataSource, sql, AuditLogRepository::mapRow, argArray);
    return new PageResult<>(filter.page(), filter.pageSize(), total, items);
  }

  public Optional<AuditLogEntry> findById(long id) {
    String sql =
        "SELECT "
            + String.join(
                ", ",
                dialect.quote("Id"),
                dialect.quote("Event"),
                dialect.quote("Source"),
                dialect.quote("Category"),
                dialect.quote("SubjectIdentifier"),
                dialect.quote("SubjectName"),
                dialect.quote("SubjectType"),
                dialect.quote("SubjectAdditionalData"),
                dialect.quote("Action"),
                dialect.quote("Data"),
                dialect.quote("Created"))
            + " FROM "
            + dialect.quote(ConfigurationTables.AUDIT_LOG)
            + " WHERE "
            + dialect.quote("Id")
            + " = ?";
    return Jdbc.queryOne(dataSource, sql, AuditLogRepository::mapRow, id);
  }

  public int insert(
      String event,
      String source,
      String category,
      String subjectIdentifier,
      String subjectName,
      String subjectType,
      String subjectAdditionalData,
      String action,
      String data) {
    String sql =
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.AUDIT_LOG)
            + " ("
            + String.join(
                ", ",
                dialect.quote("Event"),
                dialect.quote("Source"),
                dialect.quote("Category"),
                dialect.quote("SubjectIdentifier"),
                dialect.quote("SubjectName"),
                dialect.quote("SubjectType"),
                dialect.quote("SubjectAdditionalData"),
                dialect.quote("Action"),
                dialect.quote("Data"),
                dialect.quote("Created"))
            + ") VALUES (?,?,?,?,?,?,?,?,?,?)";
    return Jdbc.insertReturningId(
        dataSource,
        sql,
        nullToEmpty(event),
        nullToEmpty(source),
        nullToEmpty(category),
        nullToEmpty(subjectIdentifier),
        nullToEmpty(subjectName),
        nullToEmpty(subjectType),
        nullToEmpty(subjectAdditionalData),
        nullToEmpty(action),
        nullToEmpty(data),
        Timestamp.from(Instant.now()));
  }

  /** Delete rows with {@code Created} strictly before {@code cutoff} (UTC). */
  public int deleteOlderThan(Instant cutoff) {
    if (cutoff == null) {
      throw new IllegalArgumentException("cutoff is required");
    }
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.AUDIT_LOG)
            + " WHERE "
            + dialect.quote("Created")
            + " < ?",
        Timestamp.from(cutoff));
  }

  private void appendLike(StringBuilder where, List<Object> args, String column, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    if (where.length() > 0) {
      where.append(" AND ");
    }
    where.append(dialect.quote(column)).append(" LIKE ?");
    String stripped = value.replace("%", "").replace("_", "");
    args.add("%" + stripped + "%");
  }

  private static AuditLogEntry mapRow(ResultSet rs) throws SQLException {
    return new AuditLogEntry(
        rs.getLong("Id"),
        rs.getString("Event"),
        rs.getString("Source"),
        rs.getString("Category"),
        rs.getString("SubjectIdentifier"),
        rs.getString("SubjectName"),
        rs.getString("SubjectType"),
        rs.getString("SubjectAdditionalData"),
        rs.getString("Action"),
        rs.getString("Data"),
        readInstant(rs, "Created"));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static Instant readInstant(ResultSet rs, String column) throws SQLException {
    String text = rs.getString(column);
    if (text != null && !text.isBlank()) {
      Instant parsed = parseInstantText(text.trim());
      if (parsed != null) {
        return parsed;
      }
    }
    try {
      Timestamp ts = rs.getTimestamp(column);
      if (!rs.wasNull() && ts != null) {
        return ts.toInstant();
      }
    } catch (SQLException ignored) {
      // SQLite TEXT
    }
    return null;
  }

  static Instant parseInstantText(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String text = raw.trim();
    if (text.chars().allMatch(Character::isDigit) && text.length() >= 10 && text.length() <= 13) {
      try {
        long n = Long.parseLong(text);
        return text.length() <= 10 ? Instant.ofEpochSecond(n) : Instant.ofEpochMilli(n);
      } catch (RuntimeException ignored) {
        return null;
      }
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return OffsetDateTime.parse(text).toInstant();
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }
}
