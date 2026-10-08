package com.myano.skoruba4j.admin.security;

import com.myano.skoruba4j.admin.config.IdserverProperties;
import com.myano.skoruba4j.domain.configstore.AdminUiClientIds;
import com.myano.skoruba4j.domain.configstore.ClientConfiguration;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

/** Builds an OIDC client using IS4 {@code /connect/*} paths. Does not write the database. */
public final class StsClientRegistrationFactory {
  private StsClientRegistrationFactory() {}

  public static ClientRegistration create(IdserverProperties properties) {
    return create(properties, Optional.empty());
  }

  public static ClientRegistration create(
      IdserverProperties properties, Optional<JdbcRepositories> jdbc) {
    String issuer = properties.issuerUri();
    String redirectPath = properties.redirectPath();
    String clientId = AdminUiClientIds.resolve(properties.adminClientId(), jdbc);
    var builder =
        ClientRegistration.withRegistrationId("sts")
            .clientId(clientId)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}" + redirectPath)
            .scope(scopesFor(properties.oidcScopes(), jdbc, clientId))
            .authorizationUri(issuer + "/connect/authorize")
            .tokenUri(issuer + "/connect/token")
            .userInfoUri(issuer + "/connect/userinfo")
            .jwkSetUri(issuer + "/.well-known/openid-configuration/jwks")
            .issuerUri(issuer)
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("STS")
            .providerConfigurationMetadata(
                Map.of("end_session_endpoint", issuer + "/connect/endsession"));
    String secret = properties.adminClientSecret();
    if (secret.isBlank()) {
      builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
    } else {
      builder
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          .clientSecret(secret);
    }
    return builder.build();
  }

  static String[] scopesFor(
      String[] requested, Optional<JdbcRepositories> jdbc, String clientId) {
    String[] want = requested == null || requested.length == 0 ? new String[] {OidcScopes.OPENID} : requested;
    List<String> allowed = allowedScopes(jdbc, clientId);
    if (allowed.isEmpty()) {
      return ensureOpenId(want);
    }
    Set<String> allowedSet = new LinkedHashSet<>(allowed);
    allowedSet.add(OidcScopes.OPENID);
    List<String> kept = new ArrayList<>();
    for (String scope : want) {
      if (scope != null && allowedSet.contains(scope) && !kept.contains(scope)) {
        kept.add(scope);
      }
    }
    return ensureOpenId(kept.toArray(String[]::new));
  }

  private static List<String> allowedScopes(Optional<JdbcRepositories> jdbc, String clientId) {
    if (jdbc == null || jdbc.isEmpty() || clientId == null || clientId.isBlank()) {
      return List.of();
    }
    Optional<ClientConfiguration> client = jdbc.get().clients().findEnabledByClientId(clientId);
    if (client.isEmpty() || client.get().scopes() == null || client.get().scopes().isEmpty()) {
      return List.of();
    }
    return client.get().scopes();
  }

  private static String[] ensureOpenId(String[] scopes) {
    for (String scope : scopes) {
      if (OidcScopes.OPENID.equals(scope)) {
        return scopes;
      }
    }
    String[] withOpenId = new String[scopes.length + 1];
    withOpenId[0] = OidcScopes.OPENID;
    System.arraycopy(scopes, 0, withOpenId, 1, scopes.length);
    return withOpenId;
  }
}
