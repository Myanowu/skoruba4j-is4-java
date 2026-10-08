package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.ConfigurationTables;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

/** IdentityResources + ApiScopes names shown in discovery. */
public final class ScopeCatalogRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;

  public ScopeCatalogRepository(DataSource dataSource, SqlDialect dialect) {
    this.dataSource = dataSource;
    this.dialect = dialect;
  }

  public List<String> listDiscoveryNames() {
    Set<String> names = new LinkedHashSet<>();
    names.addAll(enabledDiscoveryNames(ConfigurationTables.IDENTITY_RESOURCES));
    names.addAll(enabledDiscoveryNames(ConfigurationTables.API_SCOPES));
    return List.copyOf(names);
  }

  private List<String> enabledDiscoveryNames(String table) {
    String sql =
        "SELECT "
            + dialect.quote("Name")
            + " FROM "
            + dialect.quote(table)
            + " WHERE "
            + dialect.quote("Enabled")
            + " = ? AND "
            + dialect.quote("ShowInDiscoveryDocument")
            + " = ?";
    return Jdbc.query(dataSource, sql, rs -> rs.getString(1), true, true);
  }
}
