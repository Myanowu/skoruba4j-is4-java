package com.myano.skoruba4j.sts.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myano.skoruba4j.protocol.Is4ClientSecretPasswordEncoder;
import com.myano.skoruba4j.protocol.Is4Paths;
import com.myano.skoruba4j.sts.StsApplication;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * P0-2: IS4 {@code /connect/introspect} and {@code /connect/revocation} must work end-to-end for a
 * confidential client (client_credentials).
 */
@SpringBootTest(
    classes = {StsApplication.class, StsIntrospectRevocationTest.Overrides.class},
    properties = {
      "spring.profiles.active=oss",
      "idserver.db.url=",
      "idserver.issuer-uri=http://127.0.0.1:5050",
      "server.ssl.enabled=false",
      "server.port=0"
    })
@AutoConfigureMockMvc
class StsIntrospectRevocationTest {

  private static final String CLIENT_ID = "api-machine";
  private static final String CLIENT_SECRET = "machine-secret";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void discoveryAdvertisesIs4IntrospectAndRevocation() throws Exception {
    MvcResult result =
        mvc.perform(get(Is4Paths.DISCOVERY).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.introspection_endpoint").value("http://127.0.0.1:5050/connect/introspect"))
            .andExpect(jsonPath("$.revocation_endpoint").value("http://127.0.0.1:5050/connect/revocation"))
            .andReturn();
    JsonNode body = json.readTree(result.getResponse().getContentAsString());
    assertTrue(body.path("grant_types_supported").toString().contains("client_credentials"));
  }

  @Test
  void clientCredentialsTokenIntrospectThenRevoke() throws Exception {
    String accessToken = requestClientCredentialsToken();
    assertNotNull(accessToken);

    MvcResult active =
        mvc.perform(
                post(Is4Paths.INTROSPECT)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(CLIENT_ID, CLIENT_SECRET))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("token", accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode introspect = json.readTree(active.getResponse().getContentAsString());
    assertTrue(introspect.path("active").asBoolean(), introspect.toString());
    assertEquals(CLIENT_ID, introspect.path("client_id").asText());

    mvc.perform(
            post(Is4Paths.REVOCATION)
                .header(HttpHeaders.AUTHORIZATION, basicAuth(CLIENT_ID, CLIENT_SECRET))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", accessToken)
                .param("token_type_hint", "access_token"))
        .andExpect(status().isOk());

    MvcResult inactive =
        mvc.perform(
                post(Is4Paths.INTROSPECT)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(CLIENT_ID, CLIENT_SECRET))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("token", accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode after = json.readTree(inactive.getResponse().getContentAsString());
    assertFalse(after.path("active").asBoolean(), after.toString());
  }

  private String requestClientCredentialsToken() throws Exception {
    MvcResult token =
        mvc.perform(
                post(Is4Paths.TOKEN)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(CLIENT_ID, CLIENT_SECRET))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
                    .param("scope", "api"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = json.readTree(token.getResponse().getContentAsString());
    String access = body.path("access_token").asText(null);
    assertNotNull(access, body.toString());
    return access;
  }

  private static String basicAuth(String clientId, String secret) {
    String raw = clientId + ":" + secret;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  @TestConfiguration
  static class Overrides {
    @Bean
    @Primary
    RegisteredClientRepository machineClient() {
      return new InMemoryRegisteredClientRepository(
          RegisteredClient.withId(UUID.randomUUID().toString())
              .clientId(CLIENT_ID)
              .clientSecret(Is4ClientSecretPasswordEncoder.sha256Base64(CLIENT_SECRET))
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
              .scope("api")
              .build());
    }
  }
}
