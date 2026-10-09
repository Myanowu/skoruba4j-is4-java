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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class ClientRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;

  public ClientRepository(DataSource dataSource, SqlDialect dialect) {
    this.dataSource = dataSource;
    this.dialect = dialect;
  }

  public int count() {
    return Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + dialect.quote(ConfigurationTables.CLIENTS));
  }

  public List<ClientSummary> list(int limit) {
    String cols =
        String.join(
            ", ",
            dialect.quote("Id"),
            dialect.quote("ClientId"),
            dialect.quote("ClientName"),
            dialect.quote("Enabled"));
    String from =
        "FROM "
            + dialect.quote(ConfigurationTables.CLIENTS)
            + " ORDER BY "
            + dialect.quote("ClientId");
    String sql = dialect.selectLimited(cols, from, limit);
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new ClientSummary(
                rs.getInt("Id"),
                rs.getString("ClientId"),
                rs.getString("ClientName"),
                rs.getBoolean("Enabled")));
  }

  public List<ClientSummary> listEnabled(int limit) {
    String cols =
        String.join(
            ", ",
            dialect.quote("Id"),
            dialect.quote("ClientId"),
            dialect.quote("ClientName"),
            dialect.quote("Enabled"));
    String from =
        "FROM "
            + dialect.quote(ConfigurationTables.CLIENTS)
            + " WHERE "
            + dialect.quote("Enabled")
            + " = ? ORDER BY "
            + dialect.quote("ClientId");
    String sql = dialect.selectLimited(cols, from, limit);
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new ClientSummary(
                rs.getInt("Id"),
                rs.getString("ClientId"),
                rs.getString("ClientName"),
                rs.getBoolean("Enabled")),
        true);
  }

  public List<String> listEnabledAdminUiClientIds() {
    String clients = dialect.quote(ConfigurationTables.CLIENTS);
    String redirects = dialect.quote(ConfigurationTables.CLIENT_REDIRECT_URIS);
    String sql =
        "SELECT DISTINCT c."
            + dialect.quote("ClientId")
            + " FROM "
            + clients
            + " c INNER JOIN "
            + redirects
            + " r ON r."
            + dialect.quote("ClientId")
            + " = c."
            + dialect.quote("Id")
            + " WHERE c."
            + dialect.quote("Enabled")
            + " = ? AND r."
            + dialect.quote("RedirectUri")
            + " LIKE ? ORDER BY c."
            + dialect.quote("ClientId");
    return Jdbc.query(dataSource, sql, rs -> rs.getString(1), true, "%/signin-oidc%");
  }

  public Optional<ClientConfiguration> findEnabledByClientId(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      return Optional.empty();
    }
    String cols = coreColumns();
    String sql =
        "SELECT "
            + cols
            + " FROM "
            + dialect.quote(ConfigurationTables.CLIENTS)
            + " WHERE "
            + dialect.quote("ClientId")
            + " = ? AND "
            + dialect.quote("Enabled")
            + " = ?";
    return loadConfiguration(sql, clientId, true);
  }

  public Optional<String> findEnabledAdminUiClientId() {
    Optional<String> local = findEnabledClientIdByRedirectLike("%localhost:6061/signin-oidc");
    if (local.isPresent()) {
      return local;
    }
    return findEnabledClientIdByRedirectLike("%/signin-oidc%");
  }

  Optional<String> findEnabledClientIdByRedirectLike(String like) {
    String clients = dialect.quote(ConfigurationTables.CLIENTS);
    String redirects = dialect.quote(ConfigurationTables.CLIENT_REDIRECT_URIS);
    String from =
        "FROM "
            + clients
            + " c INNER JOIN "
            + redirects
            + " r ON r."
            + dialect.quote("ClientId")
            + " = c."
            + dialect.quote("Id")
            + " WHERE c."
            + dialect.quote("Enabled")
            + " = ? AND r."
            + dialect.quote("RedirectUri")
            + " LIKE ? ORDER BY c."
            + dialect.quote("ClientId");
    String sql = dialect.selectLimited("c." + dialect.quote("ClientId"), from, 1);
    return Jdbc.queryOne(dataSource, sql, rs -> rs.getString(1), true, like);
  }

  public Optional<ClientConfiguration> findEnabledByPk(int id) {
    String sql =
        "SELECT "
            + coreColumns()
            + " FROM "
            + dialect.quote(ConfigurationTables.CLIENTS)
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote("Enabled")
            + " = ?";
    return loadConfiguration(sql, id, true);
  }

  /**
   * Child tables are loaded after the parent row is closed. SQLite installs use Hikari
   * {@code maximumPoolSize=1}; nested queries inside the parent {@code ResultSet} deadlock.
   */
  private Optional<ClientConfiguration> loadConfiguration(String sql, Object... args) {
    Optional<ClientConfiguration> core =
        Jdbc.queryOne(
            dataSource,
            sql,
            rs ->
                new ClientConfiguration(
                    rs.getInt("Id"),
                    rs.getString("ClientId"),
                    rs.getString("ClientName"),
                    rs.getBoolean("Enabled"),
                    rs.getBoolean("RequireClientSecret"),
                    rs.getBoolean("RequirePkce"),
                    rs.getBoolean("AllowOfflineAccess"),
                    rs.getBoolean("RequireConsent"),
                    rs.getBoolean("AlwaysIncludeUserClaimsInIdToken"),
                    rs.getInt("IdentityTokenLifetime"),
                    rs.getInt("AccessTokenLifetime"),
                    rs.getInt("AuthorizationCodeLifetime"),
                    rs.getInt("AbsoluteRefreshTokenLifetime"),
                    rs.getInt("SlidingRefreshTokenLifetime"),
                    rs.getInt("AccessTokenType"),
                    nullToEmpty(rs.getString("FrontChannelLogoutUri")),
                    nullToEmpty(rs.getString("BackChannelLogoutUri")),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    rs.getBoolean("AlwaysSendClientClaims"),
                    rs.getString("ClientClaimsPrefix")),
            args);
    return core.map(this::withChildRows);
  }

  private ClientConfiguration withChildRows(ClientConfiguration core) {
    int id = core.id();
    return new ClientConfiguration(
        id,
        core.clientId(),
        core.clientName(),
        core.enabled(),
        core.requireClientSecret(),
        core.requirePkce(),
        core.allowOfflineAccess(),
        core.requireConsent(),
        core.alwaysIncludeUserClaimsInIdToken(),
        core.identityTokenLifetime(),
        core.accessTokenLifetime(),
        core.authorizationCodeLifetime(),
        core.absoluteRefreshTokenLifetime(),
        core.slidingRefreshTokenLifetime(),
        core.accessTokenType(),
        core.frontChannelLogoutUri(),
        core.backChannelLogoutUri(),
        strings(ConfigurationTables.CLIENT_GRANT_TYPES, "GrantType", id),
        strings(ConfigurationTables.CLIENT_SCOPES, "Scope", id),
        strings(ConfigurationTables.CLIENT_REDIRECT_URIS, "RedirectUri", id),
        strings(ConfigurationTables.CLIENT_POST_LOGOUT_REDIRECT_URIS, "PostLogoutRedirectUri", id),
        strings(ConfigurationTables.CLIENT_CORS_ORIGINS, "Origin", id),
        claims(id),
        properties(id),
        secrets(id),
        core.alwaysSendClientClaims(),
        core.clientClaimsPrefix());
  }

  private List<String> strings(String table, String column, int clientPk) {
    String sql =
        "SELECT "
            + dialect.quote(column)
            + " FROM "
            + dialect.quote(table)
            + " WHERE "
            + dialect.quote("ClientId")
            + " = ?";
    return Jdbc.query(dataSource, sql, rs -> rs.getString(1), clientPk);
  }

  private List<ClientConfiguration.ClientSecretValue> secrets(int clientPk) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Value")
            + ", "
            + dialect.quote("Type")
            + ", "
            + dialect.quote("Expiration")
            + ", "
            + dialect.quote("Description")
            + ", "
            + dialect.quote("Created")
            + " FROM "
            + dialect.quote(ConfigurationTables.CLIENT_SECRETS)
            + " WHERE "
            + dialect.quote("ClientId")
            + " = ? ORDER BY "
            + dialect.quote("Id");
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new ClientConfiguration.ClientSecretValue(
                rs.getInt("Id"),
                rs.getString("Value"),
                rs.getString("Type"),
                readInstant(rs, "Expiration"),
                rs.getString("Description"),
                readInstant(rs, "Created")),
        clientPk);
  }

  public PageResult<ClientSummary> search(PageQuery query) {
    String table = dialect.quote(ConfigurationTables.CLIENTS);
    String where = "";
    Object[] args = new Object[0];
    if (query.hasSearch()) {
      where =
          " WHERE ("
              + dialect.quote("ClientId")
              + " LIKE ? OR "
              + dialect.quote("ClientName")
              + " LIKE ?)";
      args = new Object[] {query.likeContains(), query.likeContains()};
    }
    int total =
        Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + table + where, args);
    String cols =
        String.join(
            ", ",
            dialect.quote("Id"),
            dialect.quote("ClientId"),
            dialect.quote("ClientName"),
            dialect.quote("Enabled"));
    String sql =
        dialect.selectPaged(
            cols, "FROM " + table + where, dialect.quote("ClientId"), query.offset(), query.pageSize());
    List<ClientSummary> items =
        Jdbc.query(
            dataSource,
            sql,
            rs ->
                new ClientSummary(
                    rs.getInt("Id"),
                    rs.getString("ClientId"),
                    rs.getString("ClientName"),
                    rs.getBoolean("Enabled")),
            args);
    return new PageResult<>(query.page(), query.pageSize(), total, items);
  }

  public Optional<ClientConfiguration> findByPk(int id) {
    String sql =
        "SELECT "
            + coreColumns()
            + " FROM "
            + dialect.quote(ConfigurationTables.CLIENTS)
            + " WHERE "
            + dialect.quote("Id")
            + " = ?";
    return loadConfiguration(sql, id);
  }

  public int insert(ClientWrite write) {
    Timestamp created = Timestamp.from(Instant.now());
    String sql =
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.CLIENTS)
            + " ("
            + String.join(
                ", ",
                dialect.quote("Enabled"),
                dialect.quote("ClientId"),
                dialect.quote("ProtocolType"),
                dialect.quote("RequireClientSecret"),
                dialect.quote("ClientName"),
                dialect.quote("RequireConsent"),
                dialect.quote("AllowRememberConsent"),
                dialect.quote("AlwaysIncludeUserClaimsInIdToken"),
                dialect.quote("RequirePkce"),
                dialect.quote("AllowPlainTextPkce"),
                dialect.quote("AllowAccessTokensViaBrowser"),
                dialect.quote("FrontChannelLogoutSessionRequired"),
                dialect.quote("BackChannelLogoutSessionRequired"),
                dialect.quote("FrontChannelLogoutUri"),
                dialect.quote("BackChannelLogoutUri"),
                dialect.quote("AllowOfflineAccess"),
                dialect.quote("IdentityTokenLifetime"),
                dialect.quote("AccessTokenLifetime"),
                dialect.quote("AuthorizationCodeLifetime"),
                dialect.quote("AbsoluteRefreshTokenLifetime"),
                dialect.quote("SlidingRefreshTokenLifetime"),
                dialect.quote("RefreshTokenUsage"),
                dialect.quote("RefreshTokenExpiration"),
                dialect.quote("AccessTokenType"),
                dialect.quote("EnableLocalLogin"),
                dialect.quote("IncludeJwtId"),
                dialect.quote("AlwaysSendClientClaims"),
                dialect.quote("Created"),
                dialect.quote("DeviceCodeLifetime"),
                dialect.quote("NonEditable"))
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    int id =
        Jdbc.insertReturningId(
            dataSource,
            sql,
            write.enabled(),
            write.clientId(),
            "oidc",
            write.requireClientSecret(),
            write.clientName(),
            write.requireConsent(),
            true,
            write.alwaysIncludeUserClaimsInIdToken(),
            write.requirePkce(),
            false,
            false,
            true,
            true,
            blankToNull(write.frontChannelLogoutUri()),
            blankToNull(write.backChannelLogoutUri()),
            write.allowOfflineAccess(),
            write.identityTokenLifetime() > 0 ? write.identityTokenLifetime() : 300,
            write.accessTokenLifetime() > 0 ? write.accessTokenLifetime() : 3600,
            write.authorizationCodeLifetime() > 0 ? write.authorizationCodeLifetime() : 300,
            write.absoluteRefreshTokenLifetime() > 0
                ? write.absoluteRefreshTokenLifetime()
                : 2_592_000,
            write.slidingRefreshTokenLifetime() > 0 ? write.slidingRefreshTokenLifetime() : 1_296_000,
            1,
            1,
            write.accessTokenType(),
            true,
            false,
            false,
            created,
            300,
            false);
    replaceChildren(id, write);
    return id;
  }

  public void update(int id, ClientWrite write) {
    String sql =
        "UPDATE "
            + dialect.quote(ConfigurationTables.CLIENTS)
            + " SET "
            + dialect.quote("Enabled")
            + " = ?, "
            + dialect.quote("ClientId")
            + " = ?, "
            + dialect.quote("ClientName")
            + " = ?, "
            + dialect.quote("RequireClientSecret")
            + " = ?, "
            + dialect.quote("RequirePkce")
            + " = ?, "
            + dialect.quote("AllowOfflineAccess")
            + " = ?, "
            + dialect.quote("RequireConsent")
            + " = ?, "
            + dialect.quote("AlwaysIncludeUserClaimsInIdToken")
            + " = ?, "
            + dialect.quote("IdentityTokenLifetime")
            + " = ?, "
            + dialect.quote("AccessTokenLifetime")
            + " = ?, "
            + dialect.quote("AuthorizationCodeLifetime")
            + " = ?, "
            + dialect.quote("AbsoluteRefreshTokenLifetime")
            + " = ?, "
            + dialect.quote("SlidingRefreshTokenLifetime")
            + " = ?, "
            + dialect.quote("AccessTokenType")
            + " = ?, "
            + dialect.quote("FrontChannelLogoutUri")
            + " = ?, "
            + dialect.quote("BackChannelLogoutUri")
            + " = ? WHERE "
            + dialect.quote("Id")
            + " = ?";
    Jdbc.execute(
        dataSource,
        sql,
        write.enabled(),
        write.clientId(),
        write.clientName(),
        write.requireClientSecret(),
        write.requirePkce(),
        write.allowOfflineAccess(),
        write.requireConsent(),
        write.alwaysIncludeUserClaimsInIdToken(),
        write.identityTokenLifetime(),
        write.accessTokenLifetime(),
        write.authorizationCodeLifetime(),
        write.absoluteRefreshTokenLifetime(),
        write.slidingRefreshTokenLifetime(),
        write.accessTokenType(),
        blankToNull(write.frontChannelLogoutUri()),
        blankToNull(write.backChannelLogoutUri()),
        id);
    // Claims / properties are managed via addClaim/addProperty (Skoruba-style), not replaced here.
    replaceProtocolChildren(id, write);
  }

  public void delete(int id) {
    deleteChildren(id);
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.CLIENTS)
            + " WHERE "
            + dialect.quote("Id")
            + " = ?",
        id);
  }

  public int addSecret(int clientPk, String type, String hashedValue) {
    return addSecret(clientPk, type, hashedValue, null, null);
  }

  public int addSecret(
      int clientPk, String type, String hashedValue, String description, Instant expiration) {
    String sql =
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.CLIENT_SECRETS)
            + " ("
            + dialect.quote("ClientId")
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
        clientPk,
        type == null || type.isBlank() ? "SharedSecret" : type.trim(),
        hashedValue,
        description == null || description.isBlank() ? null : description.trim(),
        expiration == null ? null : expiration.toString(),
        now.toString());
  }

  public int deleteSecret(int clientPk, int secretId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.CLIENT_SECRETS)
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote("ClientId")
            + " = ?",
        secretId,
        clientPk);
  }

  public int addClaim(int clientPk, String type, String value) {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("claim type is required");
    }
    return Jdbc.insertReturningId(
        dataSource,
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.CLIENT_CLAIMS)
            + " ("
            + dialect.quote("ClientId")
            + ", "
            + dialect.quote("Type")
            + ", "
            + dialect.quote("Value")
            + ") VALUES (?, ?, ?)",
        clientPk,
        type.trim(),
        value == null ? "" : value);
  }

  public int deleteClaim(int clientPk, int claimId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.CLIENT_CLAIMS)
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote("ClientId")
            + " = ?",
        claimId,
        clientPk);
  }

  public int addProperty(int clientPk, String key, String value) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("property key is required");
    }
    return Jdbc.insertReturningId(
        dataSource,
        "INSERT INTO "
            + dialect.quote(ConfigurationTables.CLIENT_PROPERTIES)
            + " ("
            + dialect.quote("ClientId")
            + ", "
            + dialect.quote("Key")
            + ", "
            + dialect.quote("Value")
            + ") VALUES (?, ?, ?)",
        clientPk,
        key.trim(),
        value == null ? "" : value);
  }

  /** Replace all properties with this key for the client (0..n → single value). */
  public void setProperty(int clientPk, String key, String value) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("property key is required");
    }
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.CLIENT_PROPERTIES)
            + " WHERE "
            + dialect.quote("ClientId")
            + " = ? AND "
            + dialect.quote("Key")
            + " = ?",
        clientPk,
        key.trim());
    addProperty(clientPk, key, value);
  }

  public int deleteProperty(int clientPk, int propertyId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(ConfigurationTables.CLIENT_PROPERTIES)
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote("ClientId")
            + " = ?",
        propertyId,
        clientPk);
  }

  private void replaceChildren(int clientPk, ClientWrite write) {
    replaceProtocolChildren(clientPk, write);
    replacePairs(ConfigurationTables.CLIENT_CLAIMS, "Type", "Value", clientPk, write.claimLines());
    replacePairs(ConfigurationTables.CLIENT_PROPERTIES, "Key", "Value", clientPk, write.propertyLines());
  }

  /** Grants / scopes / URIs / CORS — not claims or properties (those use add/delete APIs). */
  private void replaceProtocolChildren(int clientPk, ClientWrite write) {
    replaceStrings(ConfigurationTables.CLIENT_GRANT_TYPES, "GrantType", clientPk, write.grantTypes());
    replaceStrings(ConfigurationTables.CLIENT_SCOPES, "Scope", clientPk, write.scopes());
    replaceStrings(ConfigurationTables.CLIENT_REDIRECT_URIS, "RedirectUri", clientPk, write.redirectUris());
    replaceStrings(
        ConfigurationTables.CLIENT_POST_LOGOUT_REDIRECT_URIS,
        "PostLogoutRedirectUri",
        clientPk,
        write.postLogoutRedirectUris());
    replaceStrings(ConfigurationTables.CLIENT_CORS_ORIGINS, "Origin", clientPk, write.corsOrigins());
  }

  private void deleteChildren(int clientPk) {
    for (String table :
        List.of(
            ConfigurationTables.CLIENT_GRANT_TYPES,
            ConfigurationTables.CLIENT_SCOPES,
            ConfigurationTables.CLIENT_REDIRECT_URIS,
            ConfigurationTables.CLIENT_POST_LOGOUT_REDIRECT_URIS,
            ConfigurationTables.CLIENT_SECRETS,
            ConfigurationTables.CLIENT_CORS_ORIGINS,
            ConfigurationTables.CLIENT_CLAIMS,
            ConfigurationTables.CLIENT_PROPERTIES)) {
      Jdbc.execute(
          dataSource,
          "DELETE FROM " + dialect.quote(table) + " WHERE " + dialect.quote("ClientId") + " = ?",
          clientPk);
    }
  }

  private void replaceStrings(String table, String column, int clientPk, List<String> values) {
    Jdbc.execute(
        dataSource,
        "DELETE FROM " + dialect.quote(table) + " WHERE " + dialect.quote("ClientId") + " = ?",
        clientPk);
    if (values == null) {
      return;
    }
    String sql =
        "INSERT INTO "
            + dialect.quote(table)
            + " ("
            + dialect.quote("ClientId")
            + ", "
            + dialect.quote(column)
            + ") VALUES (?, ?)";
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        Jdbc.execute(dataSource, sql, clientPk, value.trim());
      }
    }
  }

  private String coreColumns() {
    return String.join(
        ", ",
        dialect.quote("Id"),
        dialect.quote("ClientId"),
        dialect.quote("ClientName"),
        dialect.quote("Enabled"),
        dialect.quote("RequireClientSecret"),
        dialect.quote("RequirePkce"),
        dialect.quote("AllowOfflineAccess"),
        dialect.quote("RequireConsent"),
        dialect.quote("AlwaysIncludeUserClaimsInIdToken"),
        dialect.quote("IdentityTokenLifetime"),
        dialect.quote("AccessTokenLifetime"),
        dialect.quote("AuthorizationCodeLifetime"),
        dialect.quote("AbsoluteRefreshTokenLifetime"),
        dialect.quote("SlidingRefreshTokenLifetime"),
        dialect.quote("AccessTokenType"),
        dialect.quote("FrontChannelLogoutUri"),
        dialect.quote("BackChannelLogoutUri"),
        dialect.quote("AlwaysSendClientClaims"),
        dialect.quote("ClientClaimsPrefix"));
  }

  private List<ClientConfiguration.ClientClaim> claims(int clientPk) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Type")
            + ", "
            + dialect.quote("Value")
            + " FROM "
            + dialect.quote(ConfigurationTables.CLIENT_CLAIMS)
            + " WHERE "
            + dialect.quote("ClientId")
            + " = ?";
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new ClientConfiguration.ClientClaim(
                rs.getInt("Id"), rs.getString("Type"), rs.getString("Value")),
        clientPk);
  }

  private List<ClientConfiguration.ClientProperty> properties(int clientPk) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Key")
            + ", "
            + dialect.quote("Value")
            + " FROM "
            + dialect.quote(ConfigurationTables.CLIENT_PROPERTIES)
            + " WHERE "
            + dialect.quote("ClientId")
            + " = ?";
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new ClientConfiguration.ClientProperty(
                rs.getInt("Id"), rs.getString("Key"), rs.getString("Value")),
        clientPk);
  }

  private void replacePairs(
      String table, String leftColumn, String rightColumn, int clientPk, List<String> lines) {
    Jdbc.execute(
        dataSource,
        "DELETE FROM " + dialect.quote(table) + " WHERE " + dialect.quote("ClientId") + " = ?",
        clientPk);
    if (lines == null) {
      return;
    }
    String sql =
        "INSERT INTO "
            + dialect.quote(table)
            + " ("
            + dialect.quote("ClientId")
            + ", "
            + dialect.quote(leftColumn)
            + ", "
            + dialect.quote(rightColumn)
            + ") VALUES (?, ?, ?)";
    for (String line : lines) {
      if (line == null || line.isBlank()) {
        continue;
      }
      int eq = line.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      String left = line.substring(0, eq).trim();
      String right = line.substring(eq + 1).trim();
      if (!left.isEmpty()) {
        Jdbc.execute(dataSource, sql, clientPk, left, right);
      }
    }
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
    // sqlite-jdbc setObject(Timestamp) sometimes persists epoch millis as plain digits.
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
    } catch (RuntimeException ignored) {
      // continue
    }
    if (text.length() == 10) {
      try {
        return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (RuntimeException ignored) {
        return null;
      }
    }
    // .NET / sqlite-jdbc often: "yyyy-MM-dd HH:mm:ss.FFFFFFF" (7 fraction digits)
    String normalized = padFractionalSeconds(text.replace(' ', 'T'));
    try {
      if (normalized.endsWith("Z") || hasZoneOffset(normalized)) {
        return Instant.parse(normalized);
      }
      return Instant.parse(normalized + "Z");
    } catch (RuntimeException ignored) {
      // continue
    }
    try {
      return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  /** Pad/truncate fractional seconds to 9 digits so Instant.parse accepts .NET 7-digit ticks. */
  static String padFractionalSeconds(String isoLocal) {
    int dot = isoLocal.lastIndexOf('.');
    if (dot < 0) {
      return isoLocal;
    }
    int end = dot + 1;
    while (end < isoLocal.length() && Character.isDigit(isoLocal.charAt(end))) {
      end++;
    }
    String frac = isoLocal.substring(dot + 1, end);
    String suffix = isoLocal.substring(end);
    if (frac.length() >= 9) {
      frac = frac.substring(0, 9);
    } else {
      frac = String.format("%-9s", frac).replace(' ', '0');
    }
    return isoLocal.substring(0, dot + 1) + frac + suffix;
  }

  private static boolean hasZoneOffset(String isoLocalOrOffset) {
    int t = isoLocalOrOffset.indexOf('T');
    return t > 0
        && (isoLocalOrOffset.indexOf('+', t + 1) > 0 || isoLocalOrOffset.indexOf('-', t + 1) > 0);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
