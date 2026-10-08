package com.myano.skoruba4j.sts.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.sts.StsApplication;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {StsApplication.class, StsLoginThenAuthorizeTest.Overrides.class},
    properties = {
      "spring.profiles.active=oss",
      "idserver.db.url=",
      "idserver.issuer-uri=http://127.0.0.1:5050",
      "server.ssl.enabled=false",
      "server.port=0"
    })
@AutoConfigureMockMvc
class StsLoginThenAuthorizeTest {

  @Autowired MockMvc mvc;

  @Test
  void loginThenAuthorizeDoesNotBounceToLogin() throws Exception {
    String returnUrl =
        "/connect/authorize?response_type=code&client_id=test-admin&redirect_uri=http://127.0.0.1/cb&scope=openid";
    MvcResult login =
        mvc.perform(
                post("/login")
                    .param("username", "alice")
                    .param("password", "secret")
                    .param("ReturnUrl", returnUrl))
            .andExpect(status().is3xxRedirection())
            .andExpect(
                result -> {
                  String loc = result.getResponse().getRedirectedUrl();
                  if (loc == null || !loc.startsWith("/connect/authorize")) {
                    throw new AssertionError("expected authorize redirect, got " + loc);
                  }
                })
            .andReturn();
    var session =
        (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession();
    var authorize =
        mvc.perform(get(returnUrl).accept("text/html").session(session)).andReturn();
    String loc = authorize.getResponse().getRedirectedUrl();
    if (loc != null && loc.contains("/login")) {
      throw new AssertionError("fresh login should not hit account chooser: " + loc);
    }
    if (authorize.getResponse().getStatus() >= 500) {
      throw new AssertionError(
          "authorize 5xx " + authorize.getResponse().getStatus() + " " + loc);
    }
  }

  @Test
  void forgotPasswordPageIsPublic() throws Exception {
    mvc.perform(get("/forgot-password")).andExpect(status().isOk());
  }

  @Test
  void secondAuthorizeShowsChooserAgain() throws Exception {
    String returnUrl =
        "/connect/authorize?response_type=code&client_id=test-admin&redirect_uri=http://127.0.0.1/cb&scope=openid";
    MvcResult login =
        mvc.perform(
                post("/login")
                    .param("username", "alice")
                    .param("password", "secret")
                    .param("ReturnUrl", returnUrl))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var session =
        (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession();
    mvc.perform(get(returnUrl).accept("text/html").session(session)).andReturn();
    MvcResult again =
        mvc.perform(get(returnUrl).accept("text/html").session(session)).andReturn();
    String loc = again.getResponse().getRedirectedUrl();
    if (loc == null || !loc.startsWith("/login/choose")) {
      throw new AssertionError("second authorize should offer chooser again, got " + loc);
    }
  }

  @Test
  void pkceAdminClientAuthorizeRedirectsToCallback() throws Exception {
    String challenge = s256("verifier-verifier-verifier-verifier-12");
    String authorizeUrl =
        "/connect/authorize?response_type=code&client_id=pkce-admin"
            + "&redirect_uri=https://localhost:6061/signin-oidc"
            + "&scope=openid"
            + "&code_challenge="
            + challenge
            + "&code_challenge_method=S256&state=st";
    MvcResult login =
        mvc.perform(
                post("/login")
                    .param("username", "alice")
                    .param("password", "secret")
                    .param("ReturnUrl", authorizeUrl))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var session =
        (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession();
    String afterLogin = login.getResponse().getRedirectedUrl();
    if (afterLogin == null || !afterLogin.startsWith("/connect/authorize")) {
      throw new AssertionError("expected authorize after fresh login, got " + afterLogin);
    }
    MvcResult authorize =
        mvc.perform(get(authorizeUrl).accept("text/html").session(session)).andReturn();
    if (authorize.getResponse().getStatus() >= 500) {
      throw new AssertionError(
          "authorize 5xx "
              + authorize.getResponse().getStatus()
              + " "
              + authorize.getResponse().getRedirectedUrl()
              + " "
              + authorize.getResponse().getContentAsString());
    }
    String loc = authorize.getResponse().getRedirectedUrl();
    if (loc == null
        || !loc.startsWith("https://localhost:6061/signin-oidc")
        || loc.contains("error=")) {
      throw new AssertionError(
          "expected callback redirect, status="
              + authorize.getResponse().getStatus()
              + " loc="
              + loc
              + " body="
              + authorize.getResponse().getContentAsString());
    }
  }

  private static String s256(String verifier) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }

  @TestConfiguration
  static class Overrides {
    @Bean
    @Primary
    UserDetailsService testUsers(IdentityPasswordHasher hasher) {
      return username ->
          User.withUsername("alice")
              .password(hasher.hash("secret"))
              .authorities(List.of(() -> "User"))
              .build();
    }

    @Bean
    @Primary
    RegisteredClientRepository testClients() {
      return new InMemoryRegisteredClientRepository(
          RegisteredClient.withId("1")
              .clientId("test-admin")
              .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("http://127.0.0.1/cb")
              .scope("openid")
              .clientSettings(
                  ClientSettings.builder()
                      .requireProofKey(false)
                      .requireAuthorizationConsent(false)
                      .build())
              .build(),
          RegisteredClient.withId("2")
              .clientId("pkce-admin")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .clientSecret("hashed-secret")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("https://rp.example/signin-oidc")
              .redirectUri("https://localhost:6061/signin-oidc")
              .scope("openid")
              .scope("profile")
              .scope("email")
              .scope("roles")
              .clientSettings(
                  ClientSettings.builder()
                      .requireProofKey(true)
                      .requireAuthorizationConsent(false)
                      .build())
              .build());
    }
  }
}
