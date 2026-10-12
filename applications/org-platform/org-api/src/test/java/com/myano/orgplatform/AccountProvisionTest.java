package com.myano.orgplatform;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myano.orgplatform.config.OrgPlatformProperties;
import com.myano.orgplatform.idp.HttpIdpDirectory;
import com.myano.orgplatform.idp.IdpDirectory;
import com.myano.orgplatform.idp.IdpDirectory.IdpAccount;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

@SpringBootTest
@AutoConfigureMockMvc
class AccountProvisionTest {

  static Path tempDir;

  @BeforeAll
  static void temp() throws Exception {
    tempDir = Files.createTempDirectory("orgplatform-account");
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("orgplatform.data-dir", () -> tempDir.toString());
    registry.add(
        "spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("account.sqlite"));
  }

  @Autowired MockMvc mvc;

  @MockitoBean IdpDirectory idp;

  @Test
  void provisionStoresUsersIdAndRefusesATakenLogin() throws Exception {
    mvc.perform(post("/api/org/demo/seed-chart")).andExpect(status().isOk());
    long alice = personId("A001");
    when(idp.enabled()).thenReturn(false);

    mvc.perform(
            post("/api/org/people/" + alice + "/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userName":"A001","email":"alice@example.com","password":"Passw0rd!"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", containsString("尚未連接 STS")));

    when(idp.enabled()).thenReturn(true);
    when(idp.search("A001")).thenReturn(List.of());
    when(idp.create(eq("A001"), eq("alice@example.com"), eq("Passw0rd!")))
        .thenReturn(new IdpAccount("11111111-1111-1111-1111-111111111111", "A001", "alice@example.com"));

    mvc.perform(
            post("/api/org/people/" + alice + "/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userName":"A001","email":"alice@example.com","password":"Passw0rd!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.idpUserId").value("11111111-1111-1111-1111-111111111111"))
        .andExpect(jsonPath("$.loginName").value("A001"));

    long extra = createPerson();
    when(idp.search("T900"))
        .thenReturn(List.of(new IdpAccount("22222222-2222-2222-2222-222222222222", "T900", "t@example.com")));
    mvc.perform(
            post("/api/org/people/" + extra + "/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userName":"T900","email":"t@example.com","password":"Passw0rd!"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("登入名已存在，請改用關聯已有帳號"));

    mvc.perform(
            post("/api/org/people/" + extra + "/account/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"idpUserId":"11111111-1111-1111-1111-111111111111"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("此帳號已關聯其他人員"));
    verify(idp, never()).findById(anyString());
  }

  @Test
  void httpClientReadsUsersIdFromAdminApi() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(containsString("/api/Users")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {"users":[{"id":"user-guid","userName":"A001","email":"a@b.c","emailConfirmed":true}]}
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(containsString("/api/Users/user-guid")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {"id":"user-guid","userName":"A001","email":"a@b.c"}
                """,
                MediaType.APPLICATION_JSON));
    OrgPlatformProperties properties = new OrgPlatformProperties();
    properties.getIdp().setSyncEnabled(true);
    properties.getIdp().setAdminApiBaseUrl("http://idp.test");
    HttpIdpDirectory directory = new HttpIdpDirectory(properties, builder);
    assertEquals("user-guid", directory.search("A001").get(0).id());
    Optional<IdpAccount> found = directory.findById("user-guid");
    assertEquals("A001", found.orElseThrow().userName());
    server.verify();
  }

  private long personId(String employeeNo) throws Exception {
    String body =
        mvc.perform(get("/api/org/directory")).andReturn().getResponse().getContentAsString();
    JSONArray ids =
        JsonPath.read(body, "$.people[?(@.employeeNo=='" + employeeNo + "')].personId");
    return ((Number) ids.get(0)).longValue();
  }

  private long createPerson() throws Exception {
    String body =
        mvc.perform(get("/api/org/units")).andReturn().getResponse().getContentAsString();
    JSONArray ids = JsonPath.read(body, "$[?(@.code=='FIN')].id");
    long fin = ((Number) ids.get(0)).longValue();
    String created =
        mvc.perform(
                post("/api/org/people")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"displayName":"測試員","employeeNo":"T900","orgUnitId":%s,"primary":true}
                        """
                            .formatted(fin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(created, "$.id")).longValue();
  }
}
