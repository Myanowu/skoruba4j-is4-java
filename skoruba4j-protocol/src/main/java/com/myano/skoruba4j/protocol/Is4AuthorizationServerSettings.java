package com.myano.skoruba4j.protocol;

import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

public final class Is4AuthorizationServerSettings {
  private Is4AuthorizationServerSettings() {}

  public static AuthorizationServerSettings create(String issuer) {
    AuthorizationServerSettings.Builder builder =
        AuthorizationServerSettings.builder()
            .authorizationEndpoint(Is4Paths.AUTHORIZE)
            .tokenEndpoint(Is4Paths.TOKEN)
            .tokenIntrospectionEndpoint(Is4Paths.INTROSPECT)
            .tokenRevocationEndpoint(Is4Paths.REVOCATION)
            .jwkSetEndpoint(Is4Paths.JWKS)
            .oidcUserInfoEndpoint(Is4Paths.USERINFO)
            .oidcLogoutEndpoint(Is4Paths.END_SESSION)
            .deviceAuthorizationEndpoint(Is4Paths.DEVICE_AUTHORIZATION);
    if (issuer != null && !issuer.isBlank()) {
      builder.issuer(issuer.trim());
    }
    return builder.build();
  }
}
