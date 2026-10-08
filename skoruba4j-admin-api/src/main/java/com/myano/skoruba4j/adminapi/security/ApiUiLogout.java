package com.myano.skoruba4j.adminapi.security;

import com.myano.skoruba4j.adminapi.config.IdserverProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

/** Local form → {@code /login}; OIDC session → STS endsession. */
public final class ApiUiLogout {
  private ApiUiLogout() {}

  public static LogoutSuccessHandler successHandler(
      IdserverProperties properties, ClientRegistrationRepository registrations) {
    SimpleUrlLogoutSuccessHandler local = new SimpleUrlLogoutSuccessHandler();
    local.setDefaultTargetUrl("/login");
    if (!properties.apiLoginMode().usesStsOidc() || registrations == null) {
      return local;
    }
    OidcClientInitiatedLogoutSuccessHandler oidc =
        new OidcClientInitiatedLogoutSuccessHandler(registrations);
    oidc.setPostLogoutRedirectUri("{baseUrl}/signout-callback-oidc");
    return (request, response, authentication) -> {
      if (isOidcSession(authentication)) {
        oidc.onLogoutSuccess(request, response, authentication);
        return;
      }
      local.onLogoutSuccess(request, response, authentication);
    };
  }

  static boolean isOidcSession(Authentication authentication) {
    return authentication instanceof OAuth2AuthenticationToken token
        && token.getPrincipal() instanceof OidcUser;
  }
}
