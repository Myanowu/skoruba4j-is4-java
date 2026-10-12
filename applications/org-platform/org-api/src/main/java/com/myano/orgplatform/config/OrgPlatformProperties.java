package com.myano.orgplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orgplatform")
public class OrgPlatformProperties {

  private String dataDir = "./data";
  private final Security security = new Security();
  private final Idp idp = new Idp();

  public String getDataDir() {
    return dataDir;
  }

  public void setDataDir(String dataDir) {
    this.dataDir = dataDir;
  }

  public Security getSecurity() {
    return security;
  }

  public Idp getIdp() {
    return idp;
  }

  public static class Security {
    /** open | oidc */
    private String mode = "open";
    /** Identity role name required to use the org console. Matches idserver.admin.role. */
    private String adminRole = "MyRole";
    private String issuerUri = "http://127.0.0.1:5050";
    private String clientId = "org-platform";
    private String clientSecret = "";
    private String redirectPath = "/login/oauth2/code/sts";

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      this.mode = mode;
    }

    /** True when the console does not require an STS session. */
    public boolean isOpen() {
      return !"oidc".equalsIgnoreCase(mode);
    }

    public String getAdminRole() {
      return adminRole;
    }

    public void setAdminRole(String adminRole) {
      this.adminRole = adminRole;
    }

    public String getIssuerUri() {
      return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
      this.issuerUri = issuerUri;
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
  }

  public static class Idp {
    private String adminApiBaseUrl = "http://localhost:8001";
    private boolean syncEnabled = false;
    private String bearerToken = "";

    public String getAdminApiBaseUrl() {
      return adminApiBaseUrl;
    }

    public void setAdminApiBaseUrl(String adminApiBaseUrl) {
      this.adminApiBaseUrl = adminApiBaseUrl;
    }

    public boolean isSyncEnabled() {
      return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
      this.syncEnabled = syncEnabled;
    }

    public String getBearerToken() {
      return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
      this.bearerToken = bearerToken;
    }
  }
}
