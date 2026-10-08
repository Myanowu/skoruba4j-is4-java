package com.myano.skoruba4j.adminapi.config;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idserver")
public class IdserverProperties {
  private final Db db = new Db();
  private final Identity identity = new Identity();
  private final Admin admin = new Admin();
  private final Logout logout = new Logout();
  private final Tls tls = new Tls();
  /** Same overlay keys as STS — used only to show IdP buttons on Admin API /login. */
  private final ExternalLogin externalLogin = new ExternalLogin();
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

  public Logout getLogout() {
    return logout;
  }

  public Tls getTls() {
    return tls;
  }

  public ExternalLogin getExternalLogin() {
    return externalLogin;
  }

  public boolean googleLoginConfigured() {
    Google g = externalLogin.getGoogle();
    return g != null
        && g.getClientId() != null
        && !g.getClientId().isBlank()
        && g.getClientSecret() != null
        && !g.getClientSecret().isBlank();
  }

  public boolean microsoftLoginConfigured() {
    Microsoft m = externalLogin.getMicrosoft();
    return m != null
        && m.getClientId() != null
        && !m.getClientId().isBlank()
        && m.getClientSecret() != null
        && !m.getClientSecret().isBlank()
        && m.getTenantId() != null
        && !m.getTenantId().isBlank();
  }

  public boolean whatsappLoginConfigured() {
    Whatsapp w = externalLogin.getWhatsapp();
    return w != null
        && w.getBusinessPhone() != null
        && !w.getBusinessPhone().replaceAll("\\D", "").isBlank();
  }

  public boolean wechatLoginConfigured() {
    Wechat w = externalLogin.getWechat();
    return w != null
        && w.getAppId() != null
        && !w.getAppId().isBlank()
        && w.getAppSecret() != null
        && !w.getAppSecret().isBlank();
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

  public boolean strictTokens() {
    return "strict".equalsIgnoreCase(logout.getEndSession() == null ? "" : logout.getEndSession().trim());
  }

  public String adminRole() {
    String role = admin.getRole();
    return role == null || role.isBlank() ? "MyRole" : role.trim();
  }

  /** Browser console for Admin API ({@code /}, {@code /login}, {@code /ui/**}). Default on. */
  public boolean apiUiEnabled() {
    return admin.isApiUiEnabled();
  }

  public ApiLoginMode apiLoginMode() {
    return ApiLoginMode.fromConfig(admin.getApiLoginMode());
  }

  public String apiClientId() {
    String id = admin.getApiClientId();
    return id == null || id.isBlank() ? "skoruba4j-admin-api" : id.trim();
  }

  public String apiClientSecret() {
    return admin.getApiClientSecret() == null ? "" : admin.getApiClientSecret();
  }

  public String apiRedirectPath() {
    String path = admin.getApiRedirectPath();
    if (path == null || path.isBlank()) {
      return "/signin-oidc";
    }
    return path.startsWith("/") ? path.trim() : "/" + path.trim();
  }

  public String[] apiOidcScopes() {
    String raw = admin.getApiScopes();
    if (raw == null || raw.isBlank()) {
      return new String[] {"openid", "profile", "email", "roles"};
    }
    return java.util.Arrays.stream(raw.split("[,\\s]+"))
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
    /** When false, Admin API serves JWT {@code /api/**} only (no browser UI). */
    private boolean apiUiEnabled = true;
    /** {@code local} | {@code sts-oidc} | {@code both} (default). */
    private String apiLoginMode = "both";
    private String apiClientId = "skoruba4j-admin-api";
    private String apiClientSecret = "";
    private String apiRedirectPath = "/signin-oidc";
    private String apiScopes = "openid,profile,email,roles";

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public boolean isApiUiEnabled() {
      return apiUiEnabled;
    }

    public void setApiUiEnabled(boolean apiUiEnabled) {
      this.apiUiEnabled = apiUiEnabled;
    }

    public String getApiLoginMode() {
      return apiLoginMode;
    }

    public void setApiLoginMode(String apiLoginMode) {
      this.apiLoginMode = apiLoginMode;
    }

    public String getApiClientId() {
      return apiClientId;
    }

    public void setApiClientId(String apiClientId) {
      this.apiClientId = apiClientId;
    }

    public String getApiClientSecret() {
      return apiClientSecret;
    }

    public void setApiClientSecret(String apiClientSecret) {
      this.apiClientSecret = apiClientSecret;
    }

    public String getApiRedirectPath() {
      return apiRedirectPath;
    }

    public void setApiRedirectPath(String apiRedirectPath) {
      this.apiRedirectPath = apiRedirectPath;
    }

    public String getApiScopes() {
      return apiScopes;
    }

    public void setApiScopes(String apiScopes) {
      this.apiScopes = apiScopes;
    }
  }

  public static class Logout {
    private String endSession = "compatible";

    public String getEndSession() {
      return endSession;
    }

    public void setEndSession(String endSession) {
      this.endSession = endSession;
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

  public static class ExternalLogin {
    private final Google google = new Google();
    private final Microsoft microsoft = new Microsoft();
    private final Whatsapp whatsapp = new Whatsapp();
    private final Wechat wechat = new Wechat();

    public Google getGoogle() {
      return google;
    }

    public Microsoft getMicrosoft() {
      return microsoft;
    }

    public Whatsapp getWhatsapp() {
      return whatsapp;
    }

    public Wechat getWechat() {
      return wechat;
    }
  }

  public static class Google {
    private String clientId = "";
    private String clientSecret = "";

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
  }

  public static class Microsoft {
    private String clientId = "";
    private String clientSecret = "";
    private String tenantId = "";

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

    public String getTenantId() {
      return tenantId;
    }

    public void setTenantId(String tenantId) {
      this.tenantId = tenantId;
    }
  }

  public static class Whatsapp {
    private String businessPhone = "";

    public String getBusinessPhone() {
      return businessPhone;
    }

    public void setBusinessPhone(String businessPhone) {
      this.businessPhone = businessPhone;
    }
  }

  public static class Wechat {
    private String appId = "";
    private String appSecret = "";

    public String getAppId() {
      return appId;
    }

    public void setAppId(String appId) {
      this.appId = appId;
    }

    public String getAppSecret() {
      return appSecret;
    }

    public void setAppSecret(String appSecret) {
      this.appSecret = appSecret;
    }
  }
}
