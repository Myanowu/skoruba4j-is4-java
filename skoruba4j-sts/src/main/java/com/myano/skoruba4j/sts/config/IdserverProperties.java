package com.myano.skoruba4j.sts.config;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idserver")
public class IdserverProperties {
  private final Db db = new Db();
  private final Identity identity = new Identity();
  private final Admin admin = new Admin();
  private final Smtp smtp = new Smtp();
  private final ExternalLogin externalLogin = new ExternalLogin();
  private String issuerUri = "";

  public Db getDb() {
    return db;
  }

  public Identity getIdentity() {
    return identity;
  }

  public Admin getAdmin() {
    return admin;
  }

  public Smtp getSmtp() {
    return smtp;
  }

  public ExternalLogin getExternalLogin() {
    return externalLogin;
  }

  /** True when Google Client ID and secret are both configured. */
  public boolean googleLoginConfigured() {
    Google google = externalLogin.getGoogle();
    return google != null
        && google.getClientId() != null
        && !google.getClientId().isBlank()
        && google.getClientSecret() != null
        && !google.getClientSecret().isBlank();
  }

  /** True when Microsoft Client ID, secret, and tenant id are configured. */
  public boolean microsoftLoginConfigured() {
    Microsoft microsoft = externalLogin.getMicrosoft();
    return microsoft != null
        && microsoft.getClientId() != null
        && !microsoft.getClientId().isBlank()
        && microsoft.getClientSecret() != null
        && !microsoft.getClientSecret().isBlank()
        && microsoft.getTenantId() != null
        && !microsoft.getTenantId().isBlank();
  }

  /** True when WhatsApp business phone (wa.me) is configured for QR login. */
  public boolean whatsappLoginConfigured() {
    Whatsapp whatsapp = externalLogin.getWhatsapp();
    return whatsapp != null
        && whatsapp.getBusinessPhone() != null
        && !whatsapp.getBusinessPhone().replaceAll("\\D", "").isBlank();
  }

  /** True when WeChat Open Platform website app id + secret are configured (扫码登录). */
  public boolean wechatLoginConfigured() {
    Wechat wechat = externalLogin.getWechat();
    return wechat != null
        && wechat.getAppId() != null
        && !wechat.getAppId().isBlank()
        && wechat.getAppSecret() != null
        && !wechat.getAppSecret().isBlank();
  }

  public boolean anyExternalLoginConfigured() {
    return googleLoginConfigured() || microsoftLoginConfigured();
  }

  public boolean anyInteractiveExternalLoginConfigured() {
    return anyExternalLoginConfigured() || whatsappLoginConfigured() || wechatLoginConfigured();
  }

  public String getIssuerUri() {
    return issuerUri;
  }

  public void setIssuerUri(String issuerUri) {
    this.issuerUri = issuerUri;
  }

  public String adminClientId() {
    String id = admin.getClientId();
    return id == null || id.isBlank() ? "skoruba4j-admin" : id.trim();
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
    private String clientId = "skoruba4j-admin";

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
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

  /** WeChat Open Platform website application (网站应用扫码登录 / qrconnect). */
  public static class Wechat {
    private String appId = "";
    private String appSecret = "";
    /** When true, show WeChat on bare /login with no authorize client. */
    private boolean allowDirectLogin = false;

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

    public boolean isAllowDirectLogin() {
      return allowDirectLogin;
    }

    public void setAllowDirectLogin(boolean allowDirectLogin) {
      this.allowDirectLogin = allowDirectLogin;
    }
  }

  public static class Whatsapp {
    /** Business number for wa.me (digits or E.164). */
    private String businessPhone = "";
    /** Meta webhook verify token (hub.verify_token). */
    private String webhookVerifyToken = "";
    /** Optional App Secret for X-Hub-Signature-256. */
    private String appSecret = "";
    /** When true, show WhatsApp on bare /login with no authorize client. */
    private boolean allowDirectLogin = false;

    public String getBusinessPhone() {
      return businessPhone;
    }

    public void setBusinessPhone(String businessPhone) {
      this.businessPhone = businessPhone;
    }

    public String getWebhookVerifyToken() {
      return webhookVerifyToken;
    }

    public void setWebhookVerifyToken(String webhookVerifyToken) {
      this.webhookVerifyToken = webhookVerifyToken;
    }

    public String getAppSecret() {
      return appSecret;
    }

    public void setAppSecret(String appSecret) {
      this.appSecret = appSecret;
    }

    public boolean isAllowDirectLogin() {
      return allowDirectLogin;
    }

    public void setAllowDirectLogin(boolean allowDirectLogin) {
      this.allowDirectLogin = allowDirectLogin;
    }
  }

  public static class Google {
    private String clientId = "";
    private String clientSecret = "";
    /** When true, show Google on bare /login with no authorize client (uses link-existing). */
    private boolean allowDirectLogin = false;

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

    public boolean isAllowDirectLogin() {
      return allowDirectLogin;
    }

    public void setAllowDirectLogin(boolean allowDirectLogin) {
      this.allowDirectLogin = allowDirectLogin;
    }
  }

  public static class Microsoft {
    private String clientId = "";
    private String clientSecret = "";
    /** Directory (tenant) ID, or {@code common} / {@code organizations} / {@code consumers}. */
    private String tenantId = "";
    /** When true, show Microsoft on bare /login with no authorize client (uses link-existing). */
    private boolean allowDirectLogin = false;

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

    public boolean isAllowDirectLogin() {
      return allowDirectLogin;
    }

    public void setAllowDirectLogin(boolean allowDirectLogin) {
      this.allowDirectLogin = allowDirectLogin;
    }
  }

  public static class Smtp {
    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "";
    private boolean startTls = true;
    private String resetKey = "";

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
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

    public String getFrom() {
      return from;
    }

    public void setFrom(String from) {
      this.from = from;
    }

    public boolean isStartTls() {
      return startTls;
    }

    public void setStartTls(boolean startTls) {
      this.startTls = startTls;
    }

    public String getResetKey() {
      return resetKey;
    }

    public void setResetKey(String resetKey) {
      this.resetKey = resetKey;
    }
  }
}
