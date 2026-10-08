package com.myano.skoruba4j.domain.jdbc;

import com.myano.skoruba4j.domain.ConfigurationTables;
import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.IdentityTables;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.configstore.ApiAudienceRepository;
import com.myano.skoruba4j.domain.configstore.ApiResourceAdminRepository;
import com.myano.skoruba4j.domain.configstore.ApiResourceRepository;
import com.myano.skoruba4j.domain.configstore.AuditLogRepository;
import com.myano.skoruba4j.domain.configstore.ClientRepository;
import com.myano.skoruba4j.domain.configstore.DiscoveryResourceAdminRepository;
import com.myano.skoruba4j.domain.configstore.PersistedGrantRepository;
import com.myano.skoruba4j.domain.configstore.ScopeCatalogRepository;
import com.myano.skoruba4j.domain.identity.RoleRepository;
import com.myano.skoruba4j.domain.identity.UserRepository;
import javax.sql.DataSource;

public final class JdbcRepositories {
  private final DataSource dataSource;
  private final SqlDialect dialect;
  private final IdentityTables identityTables;
  private final UserRepository users;
  private final RoleRepository roles;
  private final ClientRepository clients;
  private final ApiResourceRepository apiResources;
  private final ApiResourceAdminRepository apiResourceAdmin;
  private final DiscoveryResourceAdminRepository apiScopes;
  private final DiscoveryResourceAdminRepository identityResources;
  private final ApiAudienceRepository audiences;
  private final ScopeCatalogRepository scopes;
  private final PersistedGrantRepository persistedGrants;
  private final AuditLogRepository auditLogs;

  public JdbcRepositories(DataSource dataSource, DbProvider provider, TableStyle tableStyle) {
    this.dataSource = dataSource;
    this.dialect = new SqlDialect(provider);
    this.identityTables = IdentityTables.forStyle(tableStyle);
    this.users = new UserRepository(dataSource, dialect, identityTables);
    this.roles = new RoleRepository(dataSource, dialect, identityTables);
    this.clients = new ClientRepository(dataSource, dialect);
    this.apiResources = new ApiResourceRepository(dataSource, dialect);
    this.apiResourceAdmin = new ApiResourceAdminRepository(dataSource, dialect);
    this.apiScopes =
        new DiscoveryResourceAdminRepository(
            dataSource,
            dialect,
            ConfigurationTables.API_SCOPES,
            ConfigurationTables.API_SCOPE_CLAIMS,
            ConfigurationTables.API_SCOPE_PROPERTIES,
            "ScopeId");
    this.identityResources =
        new DiscoveryResourceAdminRepository(
            dataSource,
            dialect,
            ConfigurationTables.IDENTITY_RESOURCES,
            ConfigurationTables.IDENTITY_RESOURCE_CLAIMS,
            ConfigurationTables.IDENTITY_RESOURCE_PROPERTIES,
            "IdentityResourceId");
    this.audiences = new ApiAudienceRepository(dataSource, dialect);
    this.scopes = new ScopeCatalogRepository(dataSource, dialect);
    this.persistedGrants = new PersistedGrantRepository(dataSource, dialect, identityTables);
    this.auditLogs = new AuditLogRepository(dataSource, dialect);
  }

  public DataSource dataSource() {
    return dataSource;
  }

  public SqlDialect dialect() {
    return dialect;
  }

  public IdentityTables identityTables() {
    return identityTables;
  }

  public UserRepository users() {
    return users;
  }

  public RoleRepository roles() {
    return roles;
  }

  public ClientRepository clients() {
    return clients;
  }

  public ApiResourceRepository apiResources() {
    return apiResources;
  }

  public ApiResourceAdminRepository apiResourceAdmin() {
    return apiResourceAdmin;
  }

  public DiscoveryResourceAdminRepository apiScopes() {
    return apiScopes;
  }

  public DiscoveryResourceAdminRepository identityResources() {
    return identityResources;
  }

  public ApiAudienceRepository audiences() {
    return audiences;
  }

  public ScopeCatalogRepository scopes() {
    return scopes;
  }

  public PersistedGrantRepository persistedGrants() {
    return persistedGrants;
  }

  public AuditLogRepository auditLogs() {
    return auditLogs;
  }

  public boolean ping() {
    return Jdbc.ping(dataSource);
  }
}
