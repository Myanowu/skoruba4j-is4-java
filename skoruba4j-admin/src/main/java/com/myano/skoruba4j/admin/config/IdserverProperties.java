package com.myano.skoruba4j.admin.config;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idserver")
public class IdserverProperties {
  private final Db db = new Db();
  private final Identity identity = new Identity();
  private final Admin admin = new Admin();
  private final Tls tls = new Tls();
  private String issuerUri = "http://127.0.0.1:5050";

  public Db getDb() {
    return db;
  }

  public Identity getIdentity() {
    return identity;
  }

  public Admin getAdmin() {
    return admin;
  }

  public Tls getTls() {
    return tls;
  }

  public String getIssuerUri() {
    return issuerUri;
  }

  public void setIssuerUri(String issuerUri) {
    this.issuerUri = issuerUri;
  }

  public String issuerUri() {
    String value = issuerUri == null ? "" : issuerUri.trim();
    return value.isEmpty() ? "http://127.0.0.1:5050" : value.replaceAll("/$", "");
  }

  public String adminRole() {
    String role = admin.getRole();
    return role == null || role.isBlank() ? "MyRole" : role.trim();
  }

  public AdminLoginMode loginMode() {
    return AdminLoginMode.fromConfig(admin.getLoginMode(), admin.isOidcEnabled());
  }

  /** True when STS OIDC client beans / authorize redirect are available. */
  public boolean oidcEnabled() {
    return loginMode().usesOidcClientBeans();
  }

  public String loginUrl() {
    if (loginMode().usesOidcClientBeans()) {
      return "/oauth2/authorization/sts";
    }
    return "/login";
  }

  public String adminClientId() {
    String id = admin.getClientId();
    return id == null || id.isBlank() ? "skoruba4j-admin" : id.trim();
  }

  public String adminClientSecret() {
    String secret = admin.getClientSecret();
    return secret == null ? "" : secret;
  }

  public String redirectPath() {
    String path = admin.getRedirectPath();
    if (path == null || path.isBlank()) {
      return "/signin-oidc";
    }
    return path.startsWith("/") ? path.trim() : "/" + path.trim();
  }

  public String[] oidcScopes() {
    String raw = admin.getScopes();
    if (raw == null || raw.isBlank()) {
      return new String[] {"openid", "profile"};
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toArray(String[]::new);
  }

  public String tlsTrustStore() {
    return tls.getTrustStore() == null ? "" : tls.getTrustStore().trim();
  }

  public String tlsTrustStorePassword() {
    return tls.getTrustStorePassword() == null ? "" : tls.getTrustStorePassword();
  }

  public String tlsTrustStoreType() {
    return tls.getTrustStoreType() == null ? "" : tls.getTrustStoreType().trim();
  }

  public boolean hasJdbcUrl() {
    return db.getUrl() != null && !db.getUrl().isBlank();
  }

  public DbProvider dbProvider() {
    return DbProvider.fromConfig(db.getProvider());
  }

  public TableStyle tableStyle() {
    return TableStyle.fromConfig(identity.getTableStyle());
  }

  public int dbPoolSize() {
    return com.myano.skoruba4j.domain.jdbc.DbPoolSizes.resolve(dbProvider(), db.getPoolSize());
  }

  public static class Db {
    private String provider = "sqlite";
    private String url = "";
    private String username = "";
    private String password = "";
    private int poolSize = 0;

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public int getPoolSize() {
      return poolSize;
    }

    public void setPoolSize(int poolSize) {
      this.poolSize = poolSize;
    }
  }

  public static class Identity {
    private String tableStyle = "skoruba";

    public String getTableStyle() {
      return tableStyle;
    }

    public void setTableStyle(String tableStyle) {
      this.tableStyle = tableStyle;
    }
  }

  public static class Admin {
    private String role = "MyRole";
    /** Preferred: local | sts-password | sts-oidc. Empty falls back to {@code oidcEnabled}. */
    private String loginMode = "";
    private boolean oidcEnabled = false;
    private String clientId = "skoruba4j-admin";
    private String clientSecret = "";
    private String redirectPath = "/signin-oidc";
    private String scopes = "openid,profile,email,roles";

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public String getLoginMode() {
      return loginMode;
    }

    public void setLoginMode(String loginMode) {
      this.loginMode = loginMode;
    }

    public boolean isOidcEnabled() {
      return oidcEnabled;
    }

    public void setOidcEnabled(boolean oidcEnabled) {
      this.oidcEnabled = oidcEnabled;
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public String getRedirectPath() {
      return redirectPath;
    }

    public void setRedirectPath(String redirectPath) {
      this.redirectPath = redirectPath;
    }

    public String getScopes() {
      return scopes;
    }

    public void setScopes(String scopes) {
      this.scopes = scopes;
    }
  }

  public static class Tls {
    private String trustStore = "";
    private String trustStorePassword = "";
    private String trustStoreType = "";

    public String getTrustStore() {
      return trustStore;
    }

    public void setTrustStore(String trustStore) {
      this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
      return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStoreType() {
      return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
    }
  }
}
