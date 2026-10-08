package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Admin CRUD for IS4 discovery-style resources ({@code ApiScopes} / {@code IdentityResources}).
 */
public final class DiscoveryResourceAdminRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;
  private final String table;
  private final String claimsTable;
  private final String propertiesTable;
  private final String childFkColumn;

  public DiscoveryResourceAdminRepository(
      DataSource dataSource,
      SqlDialect dialect,
      String table,
      String claimsTable,
      String propertiesTable,
      String childFkColumn) {
    this.dataSource = dataSource;
    this.dialect = dialect;
    this.table = table;
    this.claimsTable = claimsTable;
    this.propertiesTable = propertiesTable;
    this.childFkColumn = childFkColumn;
  }

  public PageResult<ApiResourceSummary> search(PageQuery query) {
    String quoted = dialect.quote(table);
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

  public Optional<ApiResourceSummary> findByPk(int id) {
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
            + dialect.quote(table)
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

  public Optional<DiscoveryResourceConfiguration> findFull(int id) {
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
                dialect.quote("Required"),
                dialect.quote("Emphasize"))
            + " FROM "
            + dialect.quote(table)
            + " WHERE "
            + dialect.quote("Id")
            + " = ?";
    return Jdbc.queryOne(
        dataSource,
        sql,
        rs ->
            new DiscoveryResourceConfiguration(
                rs.getInt("Id"),
                rs.getString("Name"),
                nullToEmpty(rs.getString("DisplayName")),
                nullToEmpty(rs.getString("Description")),
                rs.getBoolean("Enabled"),
                rs.getBoolean("ShowInDiscoveryDocument"),
                rs.getBoolean("Required"),
                rs.getBoolean("Emphasize"),
                userClaims(id),
                properties(id)),
        id);
  }

  public int insert(String name, String displayName, boolean enabled) {
    return insert(
        new DiscoveryResourceWrite(
            requireName(name),
            nullToEmpty(displayName),
            "",
            enabled,
            true,
            false,
            false,
            List.of()));
  }

  public int insert(DiscoveryResourceWrite write) {
    Timestamp created = Timestamp.from(Instant.now());
    String sql =
        "INSERT INTO "
            + dialect.quote(table)
            + " ("
            + String.join(
                ", ",
                dialect.quote("Enabled"),
                dialect.quote("Name"),
                dialect.quote("DisplayName"),
                dialect.quote("Description"),
                dialect.quote("Required"),
                dialect.quote("Emphasize"),
                dialect.quote("ShowInDiscoveryDocument"),
                dialect.quote("Created"),
                dialect.quote("NonEditable"))
            + ") VALUES (?,?,?,?,?,?,?,?,?)";
    int id =
        Jdbc.insertReturningId(
            dataSource,
            sql,
            write.enabled(),
            requireName(write.name()),
            nullToEmpty(write.displayName()),
            nullToEmpty(write.description()),
            write.required(),
            write.emphasize(),
            write.showInDiscoveryDocument(),
            created,
            false);
    replaceClaims(id, write.userClaims());
    return id;
  }

  public void update(int id, String name, String displayName, boolean enabled) {
    DiscoveryResourceConfiguration existing =
        findFull(id).orElseThrow(() -> new IllegalArgumentException("resource not found: " + id));
    update(
        id,
        new DiscoveryResourceWrite(
            requireName(name),
            nullToEmpty(displayName),
            existing.description(),
            enabled,
            existing.showInDiscoveryDocument(),
            existing.required(),
            existing.emphasize(),
            existing.userClaims()));
  }

  public void update(int id, DiscoveryResourceWrite write) {
    String sql =
        "UPDATE "
            + dialect.quote(table)
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
            + dialect.quote("Required")
            + " = ?, "
            + dialect.quote("Emphasize")
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
        write.required(),
        write.emphasize(),
        Timestamp.from(Instant.now()),
        id);
    replaceClaims(id, write.userClaims());
  }

  public void delete(int id) {
    for (String child : List.of(claimsTable, propertiesTable)) {
      Jdbc.execute(
          dataSource,
          "DELETE FROM "
              + dialect.quote(child)
              + " WHERE "
              + dialect.quote(childFkColumn)
              + " = ?",
          id);
    }
    Jdbc.execute(
        dataSource,
        "DELETE FROM " + dialect.quote(table) + " WHERE " + dialect.quote("Id") + " = ?",
        id);
  }

  public int addProperty(int resourcePk, String key, String value) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("property key is required");
    }
    return Jdbc.insertReturningId(
        dataSource,
        "INSERT INTO "
            + dialect.quote(propertiesTable)
            + " ("
            + dialect.quote(childFkColumn)
            + ", "
            + dialect.quote("Key")
            + ", "
            + dialect.quote("Value")
            + ") VALUES (?, ?, ?)",
        resourcePk,
        key.trim(),
        value == null ? "" : value);
  }

  public int deleteProperty(int resourcePk, int propertyId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(propertiesTable)
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote(childFkColumn)
            + " = ?",
        propertyId,
        resourcePk);
  }

  private void replaceClaims(int resourcePk, List<String> claims) {
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(claimsTable)
            + " WHERE "
            + dialect.quote(childFkColumn)
            + " = ?",
        resourcePk);
    if (claims == null) {
      return;
    }
    String sql =
        "INSERT INTO "
            + dialect.quote(claimsTable)
            + " ("
            + dialect.quote(childFkColumn)
            + ", "
            + dialect.quote("Type")
            + ") VALUES (?, ?)";
    for (String claim : claims) {
      if (claim != null && !claim.isBlank()) {
        Jdbc.execute(dataSource, sql, resourcePk, claim.trim());
      }
    }
  }

  private List<String> userClaims(int resourcePk) {
    String sql =
        "SELECT "
            + dialect.quote("Type")
            + " FROM "
            + dialect.quote(claimsTable)
            + " WHERE "
            + dialect.quote(childFkColumn)
            + " = ? ORDER BY "
            + dialect.quote("Id");
    return Jdbc.query(dataSource, sql, rs -> rs.getString(1), resourcePk);
  }

  private List<DiscoveryResourceConfiguration.Property> properties(int resourcePk) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Key")
            + ", "
            + dialect.quote("Value")
            + " FROM "
            + dialect.quote(propertiesTable)
            + " WHERE "
            + dialect.quote(childFkColumn)
            + " = ? ORDER BY "
            + dialect.quote("Id");
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new DiscoveryResourceConfiguration.Property(
                rs.getInt("Id"), rs.getString("Key"), rs.getString("Value")),
        resourcePk);
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
}
