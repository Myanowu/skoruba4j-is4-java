package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.ConfigurationTables;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Admin CRUD for ApiResources + scopes / claims / secrets / properties (IS4 tables). */
public final class ApiResourceAdminRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;

  public ApiResourceAdminRepository(DataSource dataSource, SqlDialect dialect) {
    this.dataSource = dataSource;
    this.dialect = dialect;
  }

  public PageResult<ApiResourceSummary> search(PageQuery query) {
    String quoted = dialect.quote(ConfigurationTables.API_RESOURCES);
    String where = "";
    Object[] args = new Object[0];
    if (query.hasSearch()) {
      where =
          " WHERE ("
              + dialect.quote("Name")
              + " LIKE ? OR "
              + dialect.quote("DisplayName")
              + " LIKE ?)";
      args = new Object[] {query.likeContains(), query.likeContains()};
    }
    int total = Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + quoted + where, args);
    String cols =
        String.join(
            ", ",
            dialect.quote("Id"),
            dialect.quote("Name"),
            dialect.quote("DisplayName"),
            dialect.quote("Enabled"));
    String sql =
        dialect.selectPaged(
            cols, "FROM " + quoted + where, dialect.quote("Name"), query.offset(), query.pageSize());
    List<ApiResourceSummary> items =
        Jdbc.query(
            dataSource,
            sql,
            rs ->
                new ApiResourceSummary(
                    rs.getInt("Id"),
                    rs.getString("Name"),
                    rs.getString("DisplayName"),
                    rs.getBoolean("Enabled")),
            args);
    return new PageResult<>(query.page(), query.pageSize(), total, items);
  }

  public Optional<ApiResourceSummary> findSummary(int id) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Name")
            + ", "
            + dialect.quote("DisplayName")
            + ", "
            + dialect.quote("Enabled")
            + " FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCES)
            + " WHERE "
            + dialect.quote("Id")
            + " = ?";
    return Jdbc.queryOne(
        dataSource,
        sql,
        rs ->
            new ApiResourceSummary(
                rs.getInt("Id"),
                rs.getString("Name"),
                rs.getString("DisplayName"),
                rs.getBoolean("Enabled")),
        id);
  }

  /** Alias for Admin API compatibility with NamedResourceRepository.findByPk. */
  public Optional<ApiResourceSummary> findByPk(int id) {
    return findSummary(id);
  }

  public Optional<ApiResourceConfiguration> findFull(int id) {
    String sql =
        "SELECT "
            + String.join(
                ", ",
                dialect.quote("Id"),
                dialect.quote("Name"),
                dialect.quote("DisplayName"),
                dialect.quote("Description"),
                dialect.quote("Enabled"),
                dialect.quote("ShowInDiscoveryDocument"),
                dialect.quote("AllowedAccessTokenSigningAlgorithms"))
            + " FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCES)
            + " WHERE "
            + dialect.quote("Id")
            + " = ?";
    return Jdbc.queryOne(
        dataSource,
        sql,
        rs ->
            new ApiResourceConfiguration(
                rs.getInt("Id"),
                rs.getString("Name"),
                nullToEmpty(rs.getString("DisplayName")),
                nullToEmpty(rs.getString("Description")),
                rs.getBoolean("Enabled"),
                rs.getBoolean("ShowInDiscoveryDocument"),
                nullToEmpty(rs.getString("AllowedAccessTokenSigningAlgorithms")),
                scopes(id),
                userClaims(id),
                secrets(id),
                properties(id)),
        id);
  }

  public List<String> listApiScopeNames() {
    return Jdbc.query(
        dataSource,
        "SELECT "
            + dialect.quote("Name")
            + " FROM "
            + dialect.quote(ConfigurationTables.API_SCOPES)
            + " ORDER BY "
            + dialect.quote("Name"),
        rs -> rs.getString(1));
  }

  public int insert(String name, String displayName, boolean enabled) {
    return insert(
        new ApiResourceWrite(
            requireName(name),
            nullToEmpty(displayName),
            "",
            enabled,
            true,
            "",
            List.of(),
            List.of()));
  }

  public int insert(ApiResourceWrite write) {
    Timestamp created = Timestamp.from(Instant.now());
    String sql =
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.API_RESOURCES)
            + " ("
            + String.join(
                ", ",
                dialect.quote("Enabled"),
                dialect.quote("Name"),
                dialect.quote("DisplayName"),
                dialect.quote("Description"),
                dialect.quote("ShowInDiscoveryDocument"),
                dialect.quote("AllowedAccessTokenSigningAlgorithms"),
                dialect.quote("Created"),
                dialect.quote("NonEditable"))
            + ") VALUES (?,?,?,?,?,?,?,?)";
    int id =
        Jdbc.insertReturningId(
            dataSource,
            sql,
            write.enabled(),
            requireName(write.name()),
            nullToEmpty(write.displayName()),
            nullToEmpty(write.description()),
            write.showInDiscoveryDocument(),
            nullToEmpty(write.allowedAccessTokenSigningAlgorithms()),
            created,
            false);
    replaceScopesAndClaims(id, write);
    return id;
  }

  public void update(int id, String name, String displayName, boolean enabled) {
    ApiResourceConfiguration existing =
        findFull(id).orElseThrow(() -> new IllegalArgumentException("ApiResource not found: " + id));
    update(
        id,
        new ApiResourceWrite(
            requireName(name),
            nullToEmpty(displayName),
            existing.description(),
            enabled,
            existing.showInDiscoveryDocument(),
            existing.allowedAccessTokenSigningAlgorithms(),
            existing.scopes(),
            existing.userClaims()));
  }

  public void update(int id, ApiResourceWrite write) {
    String sql =
        "UPDATE "
            + dialect.quote(ConfigurationTables.API_RESOURCES)
            + " SET "
            + dialect.quote("Name")
            + " = ?, "
            + dialect.quote("DisplayName")
            + " = ?, "
            + dialect.quote("Description")
            + " = ?, "
            + dialect.quote("Enabled")
            + " = ?, "
            + dialect.quote("ShowInDiscoveryDocument")
            + " = ?, "
            + dialect.quote("AllowedAccessTokenSigningAlgorithms")
            + " = ?, "
            + dialect.quote("Updated")
            + " = ? WHERE "
            + dialect.quote("Id")
            + " = ?";
    Jdbc.execute(
        dataSource,
        sql,
        requireName(write.name()),
        nullToEmpty(write.displayName()),
        nullToEmpty(write.description()),
        write.enabled(),
        write.showInDiscoveryDocument(),
        nullToEmpty(write.allowedAccessTokenSigningAlgorithms()),
        Timestamp.from(Instant.now()),
        id);
    replaceScopesAndClaims(id, write);
  }

  public void delete(int id) {
    for (String child :
        List.of(
            ConfigurationTables.API_RESOURCE_SCOPES,
            ConfigurationTables.API_RESOURCE_SECRETS,
            ConfigurationTables.API_RESOURCE_PROPERTIES,
            ConfigurationTables.API_RESOURCE_CLAIMS)) {
      Jdbc.execute(
          dataSource,
          "DELETE FROM "
              + dialect.quote(child)
              + " WHERE "
              + dialect.quote("ApiResourceId")
              + " = ?",
          id);
    }
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCES)
            + " WHERE "
            + dialect.quote("Id")
            + " = ?",
        id);
  }

  public int addSecret(
      int apiResourcePk, String type, String hashedValue, String description, Instant expiration) {
    String sql =
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.API_RESOURCE_SECRETS)
            + " ("
            + dialect.quote("ApiResourceId")
            + ", "
            + dialect.quote("Type")
            + ", "
            + dialect.quote("Value")
            + ", "
            + dialect.quote("Description")
            + ", "
            + dialect.quote("Expiration")
            + ", "
            + dialect.quote("Created")
            + ") VALUES (?, ?, ?, ?, ?, ?)";
    Instant now = Instant.now();
    return Jdbc.insertReturningId(
        dataSource,
        sql,
        apiResourcePk,
        type == null || type.isBlank() ? "SharedSecret" : type.trim(),
        hashedValue,
        description == null || description.isBlank() ? null : description.trim(),
        expiration == null ? null : expiration.toString(),
        now.toString());
  }

  public int deleteSecret(int apiResourcePk, int secretId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCE_SECRETS)
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote("ApiResourceId")
            + " = ?",
        secretId,
        apiResourcePk);
  }

  public int addProperty(int apiResourcePk, String key, String value) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("property key is required");
    }
    return Jdbc.insertReturningId(
        dataSource,
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.API_RESOURCE_PROPERTIES)
            + " ("
            + dialect.quote("ApiResourceId")
            + ", "
            + dialect.quote("Key")
            + ", "
            + dialect.quote("Value")
            + ") VALUES (?, ?, ?)",
        apiResourcePk,
        key.trim(),
        value == null ? "" : value);
  }

  public int deleteProperty(int apiResourcePk, int propertyId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCE_PROPERTIES)
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote("ApiResourceId")
            + " = ?",
        propertyId,
        apiResourcePk);
  }

  /** Normalize IS4 comma-separated algorithms to one-per-line for admin listPicker. */
  public static String algorithmsToLines(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    return String.join("\n", splitAlgorithms(raw));
  }

  public static String linesToAlgorithms(List<String> lines) {
    if (lines == null || lines.isEmpty()) {
      return "";
    }
    return String.join(",", lines.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
  }

  public static List<String> splitAlgorithms(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split("[,\\r\\n]+"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private void replaceScopesAndClaims(int apiResourcePk, ApiResourceWrite write) {
    replaceStrings(
        ConfigurationTables.API_RESOURCE_SCOPES, "Scope", apiResourcePk, write.scopes());
    replaceStrings(
        ConfigurationTables.API_RESOURCE_CLAIMS, "Type", apiResourcePk, write.userClaims());
  }

  private void replaceStrings(String table, String column, int apiResourcePk, List<String> values) {
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(table)
            + " WHERE "
            + dialect.quote("ApiResourceId")
            + " = ?",
        apiResourcePk);
    if (values == null) {
      return;
    }
    String sql =
        "INSERT INTO "
            + dialect.quote(table)
            + " ("
            + dialect.quote("ApiResourceId")
            + ", "
            + dialect.quote(column)
            + ") VALUES (?, ?)";
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        Jdbc.execute(dataSource, sql, apiResourcePk, value.trim());
      }
    }
  }

  private List<String> scopes(int apiResourcePk) {
    return strings(ConfigurationTables.API_RESOURCE_SCOPES, "Scope", apiResourcePk);
  }

  private List<String> userClaims(int apiResourcePk) {
    return strings(ConfigurationTables.API_RESOURCE_CLAIMS, "Type", apiResourcePk);
  }

  private List<String> strings(String table, String column, int apiResourcePk) {
    String sql =
        "SELECT "
            + dialect.quote(column)
            + " FROM "
            + dialect.quote(table)
            + " WHERE "
            + dialect.quote("ApiResourceId")
            + " = ? ORDER BY "
            + dialect.quote("Id");
    return Jdbc.query(dataSource, sql, rs -> rs.getString(1), apiResourcePk);
  }

  private List<ApiResourceConfiguration.Secret> secrets(int apiResourcePk) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Type")
            + ", "
            + dialect.quote("Description")
            + ", "
            + dialect.quote("Expiration")
            + ", "
            + dialect.quote("Created")
            + " FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCE_SECRETS)
            + " WHERE "
            + dialect.quote("ApiResourceId")
            + " = ? ORDER BY "
            + dialect.quote("Id");
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new ApiResourceConfiguration.Secret(
                rs.getInt("Id"),
                rs.getString("Type"),
                rs.getString("Description"),
                readInstant(rs, "Expiration"),
                readInstant(rs, "Created")),
        apiResourcePk);
  }

  private List<ApiResourceConfiguration.Property> properties(int apiResourcePk) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Key")
            + ", "
            + dialect.quote("Value")
            + " FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCE_PROPERTIES)
            + " WHERE "
            + dialect.quote("ApiResourceId")
            + " = ? ORDER BY "
            + dialect.quote("Id");
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new ApiResourceConfiguration.Property(
                rs.getInt("Id"), rs.getString("Key"), rs.getString("Value")),
        apiResourcePk);
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    return name.trim();
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
      // SQLite TEXT columns may not map cleanly to Timestamp.
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
