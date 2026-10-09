package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.ConfigurationTables;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;

/**
 * Claim type names requested by IdentityResources / ApiScopes / ApiResources for the granted
 * scopes (IS4 ProfileService {@code RequestedClaimTypes} analogue).
 */
public final class ResourceClaimTypesRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;

  public ResourceClaimTypesRepository(DataSource dataSource, SqlDialect dialect) {
    this.dataSource = dataSource;
    this.dialect = dialect;
  }

  /** Claim types from enabled {@code IdentityResources} whose {@code Name} is in {@code scopes}. */
  public Set<String> identityClaimTypes(Collection<String> scopes) {
    return claimTypesForNamedResources(
        ConfigurationTables.IDENTITY_RESOURCES,
        ConfigurationTables.IDENTITY_RESOURCE_CLAIMS,
        "IdentityResourceId",
        scopes);
  }

  /**
   * Claim types from enabled {@code ApiScopes} by name, plus {@code ApiResourceClaims} for API
   * resources that expose any of the granted scopes.
   */
  public Set<String> apiClaimTypes(Collection<String> scopes) {
    Set<String> types = new LinkedHashSet<>();
    types.addAll(
        claimTypesForNamedResources(
            ConfigurationTables.API_SCOPES,
            ConfigurationTables.API_SCOPE_CLAIMS,
            "ScopeId",
            scopes));
    types.addAll(apiResourceClaimTypes(scopes));
    return Set.copyOf(types);
  }

  private Set<String> claimTypesForNamedResources(
      String resourceTable, String claimsTable, String fkColumn, Collection<String> scopes) {
    List<String> names = normalizedScopeList(scopes);
    if (names.isEmpty()) {
      return Set.of();
    }
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT DISTINCT c.")
        .append(dialect.quote("Type"))
        .append(" FROM ")
        .append(dialect.quote(claimsTable))
        .append(" c INNER JOIN ")
        .append(dialect.quote(resourceTable))
        .append(" r ON r.")
        .append(dialect.quote("Id"))
        .append(" = c.")
        .append(dialect.quote(fkColumn))
        .append(" WHERE r.")
        .append(dialect.quote("Enabled"))
        .append(" = ? AND r.")
        .append(dialect.quote("Name"))
        .append(" IN (");
    appendPlaceholders(sql, names.size());
    sql.append(')');
    Object[] args = new Object[1 + names.size()];
    args[0] = true;
    for (int i = 0; i < names.size(); i++) {
      args[i + 1] = names.get(i);
    }
    List<String> rows = Jdbc.query(dataSource, sql.toString(), rs -> rs.getString(1), args);
    return normalizeTypes(rows);
  }

  private Set<String> apiResourceClaimTypes(Collection<String> scopes) {
    List<String> names = normalizedScopeList(scopes);
    if (names.isEmpty()) {
      return Set.of();
    }
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT DISTINCT c.")
        .append(dialect.quote("Type"))
        .append(" FROM ")
        .append(dialect.quote(ConfigurationTables.API_RESOURCE_CLAIMS))
        .append(" c INNER JOIN ")
        .append(dialect.quote(ConfigurationTables.API_RESOURCES))
        .append(" r ON r.")
        .append(dialect.quote("Id"))
        .append(" = c.")
        .append(dialect.quote("ApiResourceId"))
        .append(" INNER JOIN ")
        .append(dialect.quote(ConfigurationTables.API_RESOURCE_SCOPES))
        .append(" s ON s.")
        .append(dialect.quote("ApiResourceId"))
        .append(" = r.")
        .append(dialect.quote("Id"))
        .append(" WHERE r.")
        .append(dialect.quote("Enabled"))
        .append(" = ? AND s.")
        .append(dialect.quote("Scope"))
        .append(" IN (");
    appendPlaceholders(sql, names.size());
    sql.append(')');
    Object[] args = new Object[1 + names.size()];
    args[0] = true;
    for (int i = 0; i < names.size(); i++) {
      args[i + 1] = names.get(i);
    }
    List<String> rows = Jdbc.query(dataSource, sql.toString(), rs -> rs.getString(1), args);
    return normalizeTypes(rows);
  }

  private static List<String> normalizedScopeList(Collection<String> scopes) {
    if (scopes == null || scopes.isEmpty()) {
      return List.of();
    }
    Set<String> unique = new LinkedHashSet<>();
    for (String scope : scopes) {
      if (scope != null && !scope.isBlank()) {
        unique.add(scope.trim());
      }
    }
    return List.copyOf(unique);
  }

  private static Set<String> normalizeTypes(List<String> rows) {
    Set<String> types = new LinkedHashSet<>();
    if (rows == null) {
      return Set.of();
    }
    for (String type : rows) {
      if (type != null && !type.isBlank()) {
        types.add(type.trim());
      }
    }
    return Set.copyOf(types);
  }

  private static void appendPlaceholders(StringBuilder sql, int count) {
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        sql.append(',');
      }
      sql.append('?');
    }
  }

  /** Case-insensitive membership for claim type filters. */
  public static boolean allows(Set<String> requestedTypes, String claimType) {
    if (requestedTypes == null || requestedTypes.isEmpty() || claimType == null || claimType.isBlank()) {
      return false;
    }
    String needle = claimType.trim();
    for (String allowed : requestedTypes) {
      if (allowed != null && allowed.trim().equalsIgnoreCase(needle)) {
        return true;
      }
    }
    return false;
  }

  /** Lower-case copy for tests / diagnostics. */
  public static Set<String> lower(Set<String> types) {
    Set<String> out = new LinkedHashSet<>();
    if (types == null) {
      return Set.of();
    }
    for (String t : types) {
      if (t != null && !t.isBlank()) {
        out.add(t.trim().toLowerCase(Locale.ROOT));
      }
    }
    return Set.copyOf(out);
  }
}
