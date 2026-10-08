package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.ConfigurationTables;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import java.util.List;
import javax.sql.DataSource;

public final class ApiResourceRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;

  public ApiResourceRepository(DataSource dataSource, SqlDialect dialect) {
    this.dataSource = dataSource;
    this.dialect = dialect;
  }

  public int count() {
    return Jdbc.queryForInt(
        dataSource, "SELECT COUNT(*) FROM " + dialect.quote(ConfigurationTables.API_RESOURCES));
  }

  public List<ApiResourceSummary> list(int limit) {
    String cols =
        String.join(
            ", ",
            dialect.quote("Id"),
            dialect.quote("Name"),
            dialect.quote("DisplayName"),
            dialect.quote("Enabled"));
    String from =
        "FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCES)
            + " ORDER BY "
            + dialect.quote("Name");
    String sql = dialect.selectLimited(cols, from, limit);
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new ApiResourceSummary(
                rs.getInt("Id"),
                rs.getString("Name"),
                rs.getString("DisplayName"),
                rs.getBoolean("Enabled")));
  }
}
