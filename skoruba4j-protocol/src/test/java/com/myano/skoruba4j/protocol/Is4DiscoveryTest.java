package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.oidc.OidcProviderConfiguration;

class Is4DiscoveryTest {

  @Test
  void advertisesIdentityServer4FrontChannelLogoutFlags() {
    OidcProviderConfiguration.Builder builder =
        OidcProviderConfiguration.builder()
            .issuer("https://localhost:5051")
            .authorizationEndpoint("https://localhost:5051/connect/authorize")
            .tokenEndpoint("https://localhost:5051/connect/token")
            .jwkSetUrl("https://localhost:5051/.well-known/openid-configuration/jwks")
            .responseType("code")
            .grantType("authorization_code")
            .subjectType("public")
            .idTokenSigningAlgorithm("RS256");
    AuthorizationServerConfiguration.oidcDiscoveryCustomizer(Optional.empty()).accept(builder);
    OidcProviderConfiguration config = builder.build();
    assertEquals(Boolean.TRUE, config.getClaim("frontchannel_logout_supported"));
    assertEquals(Boolean.TRUE, config.getClaim("frontchannel_logout_session_supported"));
    assertEquals(Boolean.FALSE, config.getClaim("tls_client_certificate_bound_access_tokens"));
    long openid =
        config.getScopes().stream().filter("openid"::equals).count();
    assertEquals(1, openid);
  }
}
