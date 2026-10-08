package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.admin.config.IdserverProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

class AdminLogoutTest {

  @Test
  void signedOutVisitorReturnsToAdminLogin() throws Exception {
    String location = logout(handler(true), null);
    assertTrue(location.endsWith("/login") || "/login".equals(location));
  }

  @Test
  void oidcSessionUsesEndSession() throws Exception {
    Instant now = Instant.now();
    OidcIdToken token =
        new OidcIdToken("id-token", now, now.plusSeconds(60), Map.of("sub", "user-1"));
    DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("Admin")), token);
    String location =
        logout(handler(true), new OAuth2AuthenticationToken(user, user.getAuthorities(), "sts"));
    assertTrue(location.startsWith("https://localhost:5051/connect/endsession"));
    assertTrue(location.contains("post_logout_redirect_uri"));
    assertTrue(location.contains("signout-callback-oidc"));
  }

  @Test
  void formLoginReturnsToAdminLogin() throws Exception {
    String location = logout(handler(false), null);
    assertTrue(location.endsWith("/login") || "/login".equals(location));
  }

  private static String logout(LogoutSuccessHandler handler, org.springframework.security.core.Authentication auth)
      throws Exception {
    String[] location = new String[1];
    HttpServletResponse response =
        (HttpServletResponse)
            Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                  if ("sendRedirect".equals(method.getName())) {
                    location[0] = (String) args[0];
                    return null;
                  }
                  if ("encodeRedirectURL".equals(method.getName())
                      || "encodeURL".equals(method.getName())) {
                    return args[0];
                  }
                  if (method.getReturnType() == boolean.class) {
                    return false;
                  }
                  if (method.getReturnType() == int.class) {
                    return 0;
                  }
                  return null;
                });
    handler.onLogoutSuccess(logoutRequest(), response, auth);
    return location[0];
  }

  private static LogoutSuccessHandler handler(boolean oidc) {
    IdserverProperties properties = new IdserverProperties();
    properties.setIssuerUri("https://localhost:5051");
    properties.getAdmin().setOidcEnabled(oidc);
    properties.getAdmin().setClientId("MyClientId");
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("sts")
            .clientId("MyClientId")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/signin-oidc")
            .authorizationUri("https://localhost:5051/connect/authorize")
            .tokenUri("https://localhost:5051/connect/token")
            .jwkSetUri("https://localhost:5051/.well-known/openid-configuration/jwks")
            .issuerUri("https://localhost:5051")
            .userNameAttributeName("sub")
            .providerConfigurationMetadata(
                Map.of("end_session_endpoint", "https://localhost:5051/connect/endsession"))
            .build();
    return AdminLogout.successHandler(
        properties, new InMemoryClientRegistrationRepository(registration));
  }

  private static HttpServletRequest logoutRequest() {
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getScheme" -> "https";
                  case "getServerName" -> "localhost";
                  case "getServerPort" -> 6061;
                  case "getRequestURI" -> "/logout";
                  case "getContextPath" -> "";
                  case "getQueryString" -> null;
                  default -> {
                    if (method.getReturnType() == boolean.class) {
                      yield false;
                    }
                    if (method.getReturnType() == int.class) {
                      yield 0;
                    }
                    yield null;
                  }
                });
  }
}
