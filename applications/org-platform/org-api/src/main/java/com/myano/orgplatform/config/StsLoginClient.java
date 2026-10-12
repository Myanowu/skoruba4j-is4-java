package com.myano.orgplatform.config;

import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/** OIDC client for Skoruba4j STS {@code /connect/*}. Does not read or write the STS database. */
public final class StsLoginClient {
  private StsLoginClient() {}

  /** Builds the authorization-code registration used by the org console. */
  public static ClientRegistration create(OrgPlatformProperties.Security security) {
    String clientId = security.getClientId() == null ? "" : security.getClientId().trim();
    if (clientId.isEmpty()) {
      throw new IllegalStateException(
          "orgplatform.security.client-id is empty. Register an authorization-code client in Skoruba Admin.");
    }
    String issuer = trimSlash(security.getIssuerUri());
    String redirect = security.getRedirectPath();
    if (redirect == null || redirect.isBlank()) {
      redirect = "/login/oauth2/code/sts";
    }
    if (!redirect.startsWith("/")) {
      redirect = "/" + redirect;
    }
    var builder =
        ClientRegistration.withRegistrationId("sts")
            .clientId(clientId)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}" + redirect)
            .scope("openid", "profile")
            .authorizationUri(issuer + "/connect/authorize")
            .tokenUri(issuer + "/connect/token")
            .userInfoUri(issuer + "/connect/userinfo")
            .jwkSetUri(issuer + "/.well-known/openid-configuration/jwks")
            .issuerUri(issuer)
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("STS")
            .providerConfigurationMetadata(
                Map.of("end_session_endpoint", issuer + "/connect/endsession"));
    String secret = security.getClientSecret() == null ? "" : security.getClientSecret().trim();
    if (secret.isEmpty()) {
      builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
    } else {
      builder
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          .clientSecret(secret);
    }
    return builder.build();
  }

  private static String trimSlash(String issuer) {
    String value = issuer == null ? "" : issuer.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    if (value.isEmpty()) {
      throw new IllegalStateException("orgplatform.security.issuer-uri is empty.");
    }
    return value;
  }
}
