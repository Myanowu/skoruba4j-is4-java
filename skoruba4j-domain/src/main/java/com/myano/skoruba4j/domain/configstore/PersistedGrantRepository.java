package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.ConfigurationTables;
import com.myano.skoruba4j.domain.IdentityTables;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class PersistedGrantRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;
  private final IdentityTables identityTables;

  public PersistedGrantRepository(
      DataSource dataSource, SqlDialect dialect, IdentityTables identityTables) {
    this.dataSource = dataSource;
    this.dialect = dialect;
    this.identityTables = identityTables;
  }

  /**
   * Subjects that have at least one persisted grant (Skoruba PersistedGrants list). Search matches
   * subject id, user name, or email.
   */
  public PageResult<PersistedGrantSubject> searchSubjects(PageQuery query) {
    String grants = dialect.quote(ConfigurationTables.PERSISTED_GRANTS);
    String users = dialect.quote(identityTables.users());
    String from =
        "FROM (SELECT DISTINCT g."
            + dialect.quote("SubjectId")
            + " AS sid FROM "
            + grants
            + " g WHERE g."
            + dialect.quote("SubjectId")
            + " IS NOT NULL AND g."
            + dialect.quote("SubjectId")
            + " <> '') d"
            + " LEFT JOIN "
            + users
            + " u ON u."
            + dialect.quote("Id")
            + " = d.sid";
    String where = "";
    Object[] args = new Object[0];
    if (query.hasSearch()) {
      where =
          " WHERE (d.sid LIKE ? OR u."
              + dialect.quote("UserName")
              + " LIKE ? OR u."
              + dialect.quote("Email")
              + " LIKE ?)";
      String like = query.likeContains();
      args = new Object[] {like, like, like};
    }
    int total =
        Jdbc.queryForInt(dataSource, "SELECT COUNT(*) " + from + where, args);
    String cols =
        "d.sid AS SubjectId, COALESCE(u."
            + dialect.quote("UserName")
            + ", u."
            + dialect.quote("Email")
            + ", '') AS SubjectName";
    String sql =
        dialect.selectPaged(
            cols, from + where, "d.sid", true, query.offset(), query.pageSize());
    List<PersistedGrantSubject> items =
        Jdbc.query(
            dataSource,
            sql,
            rs ->
                new PersistedGrantSubject(
                    rs.getString("SubjectId"), nullToEmpty(rs.getString("SubjectName"))),
            args);
    return new PageResult<>(query.page(), query.pageSize(), total, items);
  }

  /** Paged grant list; newest {@code CreationTime} first. Search matches subject, client, or type. */
  public PageResult<PersistedGrantRecord> search(PageQuery query) {
    String table = dialect.quote(ConfigurationTables.PERSISTED_GRANTS);
    String where = "";
    Object[] args = new Object[0];
    if (query.hasSearch()) {
      where =
          " WHERE ("
              + dialect.quote("SubjectId")
              + " LIKE ? OR "
              + dialect.quote("ClientId")
              + " LIKE ? OR "
              + dialect.quote("Type")
              + " LIKE ?)";
      String like = query.likeContains();
      args = new Object[] {like, like, like};
    }
    int total = Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + table + where, args);
    String sql =
        dialect.selectPaged(
            detailColumns(),
            "FROM " + table + where,
            dialect.quote("CreationTime"),
            false,
            query.offset(),
            query.pageSize());
    List<PersistedGrantRecord> items =
        Jdbc.query(dataSource, sql, PersistedGrantRepository::mapDetail, args);
    return new PageResult<>(query.page(), query.pageSize(), total, items);
  }

  public PageResult<PersistedGrantRecord> searchBySubject(String subjectId, PageQuery query) {
    if (subjectId == null || subjectId.isBlank()) {
      return new PageResult<>(query.page(), query.pageSize(), 0, List.of());
    }
    String table = dialect.quote(ConfigurationTables.PERSISTED_GRANTS);
    // Hide SAS container + JWT access-token index rows; Admin shows code/refresh-style grants.
    String where =
        " WHERE "
            + dialect.quote("SubjectId")
            + " = ? AND "
            + dialect.quote("Type")
            + " NOT IN (?, ?)";
    Object[] args = new Object[] {subjectId, TYPE_AUTHORIZATION, TYPE_ACCESS_TOKEN};
    int total = Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + table + where, args);
    String sql =
        dialect.selectPaged(
            detailColumns(),
            "FROM " + table + where,
            dialect.quote("CreationTime"),
            false,
            query.offset(),
            query.pageSize());
    List<PersistedGrantRecord> items =
        Jdbc.query(dataSource, sql, PersistedGrantRepository::mapDetail, args);
    return new PageResult<>(query.page(), query.pageSize(), total, items);
  }

  /** Newest grants first for a subject (capped for Admin / debug views). */
  public List<PersistedGrantRecord> listBySubject(String subjectId) {
    return searchBySubject(subjectId, PageQuery.of(null, 1, 50)).items();
  }

  public Optional<PersistedGrantRecord> findByKey(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    String sql =
        "SELECT "
            + detailColumns()
            + " FROM "
            + dialect.quote(ConfigurationTables.PERSISTED_GRANTS)
            + " WHERE "
            + dialect.quote("Key")
            + " = ?";
    return Jdbc.queryOne(dataSource, sql, PersistedGrantRepository::mapDetail, key);
  }

  public Optional<String> resolveSubjectName(String subjectId) {
    if (subjectId == null || subjectId.isBlank()) {
      return Optional.empty();
    }
    String sql =
        "SELECT "
            + dialect.quote("UserName")
            + ", "
            + dialect.quote("Email")
            + " FROM "
            + dialect.quote(identityTables.users())
            + " WHERE "
            + dialect.quote("Id")
            + " = ?";
    return Jdbc.queryOne(
        dataSource,
        sql,
        rs -> {
          String name = rs.getString("UserName");
          if (name != null && !name.isBlank()) {
            return name;
          }
          String email = rs.getString("Email");
          return email == null ? "" : email;
        },
        subjectId);
  }

  public int deleteByKey(String key) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.PERSISTED_GRANTS)
            + " WHERE "
            + dialect.quote("Key")
            + " = ?",
        key);
  }

  /** Remove primary auth row and token-index rows that point at this authorization id. */
  public int deleteAuthorizationBundle(String authorizationId) {
    if (authorizationId == null || authorizationId.isBlank()) {
      return 0;
    }
    int n = deleteByKey(authorizationId);
    n +=
        Jdbc.execute(
            dataSource,
            "DELETE FROM "
                + dialect.quote(ConfigurationTables.PERSISTED_GRANTS)
                + " WHERE "
                + dialect.quote("Data")
                + " = ? AND "
                + dialect.quote("Type")
                + " <> ?",
            authorizationId,
            TYPE_AUTHORIZATION);
    return n;
  }

  public int deleteBySubject(String subjectId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.PERSISTED_GRANTS)
            + " WHERE "
            + dialect.quote("SubjectId")
            + " = ?",
        subjectId);
  }

  /**
   * Insert or replace a PersistedGrants row (IS4 operational store). Used by STS
   * {@code OAuth2AuthorizationService}.
   */
  public void upsert(
      String key,
      String type,
      String subjectId,
      String clientId,
      String sessionId,
      String description,
      String data,
      Instant creationTime,
      Instant expiration,
      Instant consumedTime) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("key is required");
    }
    deleteByKey(key);
    String sql =
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.PERSISTED_GRANTS)
            + " ("
            + String.join(
                ", ",
                dialect.quote("Key"),
                dialect.quote("Type"),
                dialect.quote("SubjectId"),
                dialect.quote("ClientId"),
                dialect.quote("SessionId"),
                dialect.quote("Description"),
                dialect.quote("Data"),
                dialect.quote("CreationTime"),
                dialect.quote("Expiration"),
                dialect.quote("ConsumedTime"))
            + ") VALUES (?,?,?,?,?,?,?,?,?,?)";
    Instant created = creationTime == null ? Instant.now() : creationTime;
    Jdbc.execute(
        dataSource,
        sql,
        key,
        type == null ? "" : type,
        subjectId,
        clientId,
        sessionId,
        description,
        data == null ? "" : data,
        Timestamp.from(created),
        expiration == null ? null : Timestamp.from(expiration),
        consumedTime == null ? null : Timestamp.from(consumedTime));
  }

  /** SAS container authorization payload row ({@code Key} = authorization id). */
  public static final String TYPE_AUTHORIZATION = "authorization";

  /** Token-index row for access tokens (incl. JWTs) so UserInfo can {@code findByToken}. */
  public static final String TYPE_ACCESS_TOKEN = "access_token";

  private String detailColumns() {
    return String.join(
        ", ",
        dialect.quote("Key"),
        dialect.quote("Type"),
        dialect.quote("SubjectId"),
        dialect.quote("ClientId"),
        dialect.quote("SessionId"),
        dialect.quote("Description"),
        dialect.quote("Data"),
        dialect.quote("CreationTime"),
        dialect.quote("Expiration"),
        dialect.quote("ConsumedTime"));
  }

  private static PersistedGrantRecord mapDetail(ResultSet rs) throws SQLException {
    return new PersistedGrantRecord(
        rs.getString("Key"),
        rs.getString("Type"),
        rs.getString("SubjectId"),
        rs.getString("ClientId"),
        rs.getString("SessionId"),
        rs.getString("Description"),
        rs.getString("Data"),
        readInstant(rs, "CreationTime"),
        readInstant(rs, "Expiration"),
        readInstant(rs, "ConsumedTime"));
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
