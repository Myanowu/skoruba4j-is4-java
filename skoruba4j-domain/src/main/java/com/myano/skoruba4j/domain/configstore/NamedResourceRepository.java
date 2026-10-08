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

public final class NamedResourceRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;
  private final String table;
  private final boolean identityStyle;
  private final String childFkColumn;
  private final List<String> childTables;

  public NamedResourceRepository(
      DataSource dataSource,
      SqlDialect dialect,
      String table,
      boolean identityStyle,
      String childFkColumn,
      List<String> childTables) {
    this.dataSource = dataSource;
    this.dialect = dialect;
    this.table = table;
    this.identityStyle = identityStyle;
    this.childFkColumn = childFkColumn;
    this.childTables = childTables == null ? List.of() : childTables;
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

  public int insert(String name, String displayName, boolean enabled) {
    Timestamp created = Timestamp.from(Instant.now());
    if (identityStyle) {
      String sql =
          "INSERT INTO "
              + dialect.quote(table)
              + " ("
              + String.join(
                  ", ",
                  dialect.quote("Enabled"),
                  dialect.quote("Name"),
                  dialect.quote("DisplayName"),
                  dialect.quote("Required"),
                  dialect.quote("Emphasize"),
                  dialect.quote("ShowInDiscoveryDocument"),
                  dialect.quote("Created"),
                  dialect.quote("NonEditable"))
              + ") VALUES (?,?,?,?,?,?,?,?)";
      return Jdbc.insertReturningId(
          dataSource, sql, enabled, name, displayName, false, false, true, created, false);
    }
    String sql =
        "INSERT INTO "
            + dialect.quote(table)
            + " ("
            + String.join(
                ", ",
                dialect.quote("Enabled"),
                dialect.quote("Name"),
                dialect.quote("DisplayName"),
                dialect.quote("ShowInDiscoveryDocument"),
                dialect.quote("Created"),
                dialect.quote("NonEditable"))
            + ") VALUES (?,?,?,?,?,?)";
    return Jdbc.insertReturningId(dataSource, sql, enabled, name, displayName, true, created, false);
  }

  public void update(int id, String name, String displayName, boolean enabled) {
    String sql =
        "UPDATE "
            + dialect.quote(table)
            + " SET "
            + dialect.quote("Name")
            + " = ?, "
            + dialect.quote("DisplayName")
            + " = ?, "
            + dialect.quote("Enabled")
            + " = ? WHERE "
            + dialect.quote("Id")
            + " = ?";
    Jdbc.execute(dataSource, sql, name, displayName, enabled, id);
  }

  public void delete(int id) {
    for (String child : childTables) {
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
}
