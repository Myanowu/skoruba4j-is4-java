package com.myano.skoruba4j.sts.security.externallogin;

import com.myano.skoruba4j.sts.config.IdserverProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

@Configuration
public class ExternalOAuth2ClientConfiguration {

  public static final String GOOGLE_REGISTRATION_ID = ExternalIdentityLinker.GOOGLE;
  public static final String MICROSOFT_REGISTRATION_ID = ExternalIdentityLinker.MICROSOFT;

  @Bean
  public ClientRegistrationRepository clientRegistrationRepository(IdserverProperties props) {
    List<ClientRegistration> registrations = new ArrayList<>();
    if (props.googleLoginConfigured()) {
      registrations.add(googleRegistration(props.getExternalLogin().getGoogle()));
    }
    if (props.microsoftLoginConfigured()) {
      registrations.add(microsoftRegistration(props.getExternalLogin().getMicrosoft()));
    }
    if (registrations.isEmpty()) {
      return registrationId -> null;
    }
    return new InMemoryClientRegistrationRepository(registrations);
  }

  private static ClientRegistration googleRegistration(IdserverProperties.Google google) {
    return ClientRegistration.withRegistrationId(GOOGLE_REGISTRATION_ID)
        .clientId(google.getClientId().trim())
        .clientSecret(google.getClientSecret().trim())
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope("openid", "profile", "email")
        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
        .tokenUri("https://oauth2.googleapis.com/token")
        .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
        .userNameAttributeName(IdTokenClaimNames.SUB)
        .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
        .clientName("Google")
        .build();
  }

  private static ClientRegistration microsoftRegistration(IdserverProperties.Microsoft microsoft) {
    String tenant = microsoft.getTenantId().trim();
    String base = "https://login.microsoftonline.com/" + tenant;
    return ClientRegistration.withRegistrationId(MICROSOFT_REGISTRATION_ID)
        .clientId(microsoft.getClientId().trim())
        .clientSecret(microsoft.getClientSecret().trim())
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope("openid", "profile", "email")
        .authorizationUri(base + "/oauth2/v2.0/authorize")
        .tokenUri(base + "/oauth2/v2.0/token")
        .jwkSetUri(base + "/discovery/v2.0/keys")
        .userNameAttributeName(IdTokenClaimNames.SUB)
        .clientName("Microsoft")
        .build();
  }
}
