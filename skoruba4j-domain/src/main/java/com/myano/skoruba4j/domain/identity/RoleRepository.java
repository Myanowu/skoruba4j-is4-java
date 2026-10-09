package com.myano.skoruba4j.domain.identity;

import com.myano.skoruba4j.domain.IdentityTables;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class RoleRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;
  private final IdentityTables tables;

  public RoleRepository(DataSource dataSource, SqlDialect dialect, IdentityTables tables) {
    this.dataSource = dataSource;
    this.dialect = dialect;
    this.tables = tables;
  }

  public PageResult<IdentityRole> search(PageQuery query) {
    if (query.hasSearch()) {
      var exact = findByNormalizedName(query.searchText());
      if (exact.isPresent()) {
        return new PageResult<>(query.page(), query.pageSize(), 1, List.of(exact.get()));
      }
    }
    String table = dialect.quote(tables.roles());
    String where = "";
    Object[] args = new Object[0];
    if (query.hasSearch()) {
      where = " WHERE " + dialect.quote("NormalizedName") + " LIKE ?";
      args = new Object[] {query.likeContains().toUpperCase()};
    }
    int total = Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + table + where, args);
    String cols =
        String.join(
            ", ",
            dialect.quote("Id"),
            dialect.quote("Name"),
            dialect.quote("NormalizedName"));
    String sql =
        dialect.selectPaged(
            cols, "FROM " + table + where, dialect.quote("NormalizedName"), query.offset(), query.pageSize());
    List<IdentityRole> items = Jdbc.query(dataSource, sql, RoleRepository::mapRow, args);
    return new PageResult<>(query.page(), query.pageSize(), total, items);
  }

  public List<String> listNames(int limit) {
    String table = dialect.quote(tables.roles());
    String from = "FROM " + table + " ORDER BY " + dialect.quote("Name");
    String sql = dialect.selectLimited(dialect.quote("Name"), from, limit);
    List<String> names = new ArrayList<>();
    for (String name : Jdbc.query(dataSource, sql, rs -> rs.getString(1))) {
      if (name != null && !name.isBlank()) {
        names.add(name.trim());
      }
    }
    return names;
  }

  public java.util.Optional<IdentityRole> findById(String id) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Name")
            + ", "
            + dialect.quote("NormalizedName")
            + " FROM "
            + dialect.quote(tables.roles())
            + " WHERE "
            + dialect.quote("Id")
            + " = ?";
    return Jdbc.queryOne(dataSource, sql, RoleRepository::mapRow, id);
  }

  public java.util.Optional<IdentityRole> findByNormalizedName(String normalizedName) {
    if (normalizedName == null || normalizedName.isBlank()) {
      return java.util.Optional.empty();
    }
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Name")
            + ", "
            + dialect.quote("NormalizedName")
            + " FROM "
            + dialect.quote(tables.roles())
            + " WHERE "
            + dialect.quote("NormalizedName")
            + " = ?";
    return Jdbc.queryOne(
        dataSource, sql, RoleRepository::mapRow, normalizedName.trim().toUpperCase());
  }

  public String insert(String name) {
    String id = UUID.randomUUID().toString();
    String normalized = name.trim().toUpperCase();
    String sql =
        "INSERT INTO "
            + dialect.quote(tables.roles())
            + " ("
            + dialect.quote("Id")
            + ", "
            + dialect.quote("Name")
            + ", "
            + dialect.quote("NormalizedName")
            + ", "
            + dialect.quote("ConcurrencyStamp")
            + ") VALUES (?, ?, ?, ?)";
    Jdbc.execute(dataSource, sql, id, name.trim(), normalized, UUID.randomUUID().toString());
    return id;
  }

  public void update(String id, String name) {
    String sql =
        "UPDATE "
            + dialect.quote(tables.roles())
            + " SET "
            + dialect.quote("Name")
            + " = ?, "
            + dialect.quote("NormalizedName")
            + " = ?, "
            + dialect.quote("ConcurrencyStamp")
            + " = ? WHERE "
            + dialect.quote("Id")
            + " = ?";
    Jdbc.execute(
        dataSource, sql, name.trim(), name.trim().toUpperCase(), UUID.randomUUID().toString(), id);
  }

  public void delete(String id) {
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.userRoles())
            + " WHERE "
            + dialect.quote("RoleId")
            + " = ?",
        id);
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.roleClaims())
            + " WHERE "
            + dialect.quote("RoleId")
            + " = ?",
        id);
    Jdbc.execute(
        dataSource,
        "DELETE FROM " + dialect.quote(tables.roles()) + " WHERE " + dialect.quote("Id") + " = ?",
        id);
  }

  public List<RoleClaim> listClaims(String roleId) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("ClaimType")
            + ", "
            + dialect.quote("ClaimValue")
            + " FROM "
            + dialect.quote(tables.roleClaims())
            + " WHERE "
            + dialect.quote("RoleId")
            + " = ? ORDER BY "
            + dialect.quote("Id");
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new RoleClaim(
                rs.getInt("Id"), rs.getString("ClaimType"), rs.getString("ClaimValue")),
        roleId);
  }

  public int addClaim(String roleId, String type, String value) {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("claim type is required");
    }
    return Jdbc.insertReturningId(
        dataSource,
        "INSERT INTO "
            + dialect.quote(tables.roleClaims())
            + " ("
            + dialect.quote("RoleId")
            + ", "
            + dialect.quote("ClaimType")
            + ", "
            + dialect.quote("ClaimValue")
            + ") VALUES (?, ?, ?)",
        roleId,
        type.trim(),
        value == null ? "" : value);
  }

  public int deleteClaim(String roleId, int claimId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.roleClaims())
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote("RoleId")
            + " = ?",
        claimId,
        roleId);
  }

  /** Users assigned to this role ({@code UserRoles}), ordered by user name. */
  public List<IdentityUser> listUsers(String roleId, int limit) {
    int take = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
    String cols =
        "u."
            + dialect.quote("Id")
            + ", u."
            + dialect.quote("UserName")
            + ", u."
            + dialect.quote("Email");
    String from =
        "FROM "
            + dialect.quote(tables.userRoles())
            + " ur INNER JOIN "
            + dialect.quote(tables.users())
            + " u ON u."
            + dialect.quote("Id")
            + " = ur."
            + dialect.quote("UserId")
            + " WHERE ur."
            + dialect.quote("RoleId")
            + " = ? ORDER BY u."
            + dialect.quote("UserName");
    String sql = dialect.selectLimited(cols, from, take);
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new IdentityUser(
                rs.getString("Id"),
                rs.getString("UserName"),
                null,
                rs.getString("Email"),
                null,
                false,
                null,
                null,
                false,
                null,
                0,
                false,
                null,
                false),
        roleId);
  }

  private static IdentityRole mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new IdentityRole(rs.getString("Id"), rs.getString("Name"), rs.getString("NormalizedName"));
  }
}
