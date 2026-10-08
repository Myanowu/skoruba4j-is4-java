package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.ConfigurationTables;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

public final class ApiAudienceRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;

  public ApiAudienceRepository(DataSource dataSource, SqlDialect dialect) {
    this.dataSource = dataSource;
    this.dialect = dialect;
  }

  public List<String> resourceNamesForScopes(Collection<String> scopes) {
    if (scopes == null || scopes.isEmpty()) {
      return List.of();
    }
    List<String> scopeList = new ArrayList<>(new LinkedHashSet<>(scopes));
    String placeholders = String.join(",", scopeList.stream().map(s -> "?").toList());
    String sql =
        "SELECT DISTINCT ar."
            + dialect.quote("Name")
            + " FROM "
            + dialect.quote(ConfigurationTables.API_RESOURCE_SCOPES)
            + " ars INNER JOIN "
            + dialect.quote(ConfigurationTables.API_RESOURCES)
            + " ar ON ar."
            + dialect.quote("Id")
            + " = ars."
            + dialect.quote("ApiResourceId")
            + " WHERE ar."
            + dialect.quote("Enabled")
            + " = ? AND ars."
            + dialect.quote("Scope")
            + " IN ("
            + placeholders
            + ") ORDER BY ar."
            + dialect.quote("Name");
    Object[] args = new Object[1 + scopeList.size()];
    args[0] = true;
    for (int i = 0; i < scopeList.size(); i++) {
      args[i + 1] = scopeList.get(i);
    }
    Set<String> names = new LinkedHashSet<>(Jdbc.query(dataSource, sql, rs -> rs.getString(1), args));
    return List.copyOf(names);
  }
}
