package com.myano.skoruba4j.sts.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.protocol.Is4ClientSecretPasswordEncoder;
import com.myano.skoruba4j.protocol.Is4Paths;
import com.myano.skoruba4j.sts.StsApplication;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** P0-3: resource-owner password grant against IS4 {@code /connect/token}. */
@SpringBootTest(
    classes = {StsApplication.class, StsPasswordGrantTest.Overrides.class},
    properties = {
      "spring.profiles.active=oss",
      "idserver.db.url=",
      "idserver.issuer-uri=http://127.0.0.1:5050",
      "server.ssl.enabled=false",
      "server.port=0"
    })
@AutoConfigureMockMvc
class StsPasswordGrantTest {

  @Autowired MockMvc mvc;

  @Test
  void passwordGrantReturnsAccessToken() throws Exception {
    String basic =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("ro-client:ro-secret".getBytes(StandardCharsets.UTF_8));
    MvcResult result =
        mvc.perform(
                post(Is4Paths.TOKEN)
                    .header(HttpHeaders.AUTHORIZATION, basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "password")
                    .param("username", "alice")
                    .param("password", "secret")
                    .param("scope", "openid"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn();
    assertNotNull(result.getResponse().getContentAsString());
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
    RegisteredClientRepository roClient() {
      return new InMemoryRegisteredClientRepository(
          RegisteredClient.withId(UUID.randomUUID().toString())
              .clientId("ro-client")
              .clientSecret(Is4ClientSecretPasswordEncoder.sha256Base64("ro-secret"))
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.PASSWORD)
              .scope("openid")
              .build());
    }
  }
}
