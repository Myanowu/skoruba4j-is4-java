package com.myano.skoruba4j.domain;

/** IS4 configuration table names (same in Skoruba). */
public final class ConfigurationTables {
  public static final String CLIENTS = "Clients";
  public static final String API_RESOURCES = "ApiResources";
  public static final String API_RESOURCE_SCOPES = "ApiResourceScopes";
  public static final String API_RESOURCE_CLAIMS = "ApiResourceClaims";
  public static final String API_RESOURCE_SECRETS = "ApiResourceSecrets";
  public static final String API_SCOPES = "ApiScopes";
  public static final String API_SCOPE_CLAIMS = "ApiScopeClaims";
  public static final String IDENTITY_RESOURCES = "IdentityResources";
  public static final String CLIENT_SCOPES = "ClientScopes";
  public static final String CLIENT_GRANT_TYPES = "ClientGrantTypes";
  public static final String CLIENT_REDIRECT_URIS = "ClientRedirectUris";
  public static final String CLIENT_SECRETS = "ClientSecrets";
  public static final String CLIENT_POST_LOGOUT_REDIRECT_URIS = "ClientPostLogoutRedirectUris";
  public static final String CLIENT_CORS_ORIGINS = "ClientCorsOrigins";
  public static final String CLIENT_CLAIMS = "ClientClaims";
  public static final String CLIENT_PROPERTIES = "ClientProperties";
  public static final String API_RESOURCE_PROPERTIES = "ApiResourceProperties";
  public static final String API_SCOPE_PROPERTIES = "ApiScopeProperties";
  public static final String IDENTITY_RESOURCE_CLAIMS = "IdentityResourceClaims";
  public static final String IDENTITY_RESOURCE_PROPERTIES = "IdentityResourceProperties";
  public static final String PERSISTED_GRANTS = "PersistedGrants";
  /** Skoruba Admin audit trail (often same DB as config). */
  public static final String AUDIT_LOG = "AuditLog";

  private ConfigurationTables() {}
}
