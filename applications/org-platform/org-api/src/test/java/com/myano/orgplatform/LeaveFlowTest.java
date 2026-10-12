package com.myano.orgplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LeaveFlowTest {

  static Path tempDir;

  @BeforeAll
  static void temp() throws Exception {
    tempDir = Files.createTempDirectory("orgplatform-flow");
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("orgplatform.data-dir", () -> tempDir.toString());
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("flow.sqlite"));
  }

  @Autowired MockMvc mvc;

  @Test
  @Order(1)
  void seedSubmitApprove() throws Exception {
    mvc.perform(post("/api/workflow/demo/seed-leave"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formCode").value("leave"));

    MvcResult units =
        mvc.perform(get("/api/org/units")).andExpect(status().isOk()).andReturn();
    net.minidev.json.JSONArray finIds =
        JsonPath.read(units.getResponse().getContentAsString(), "$[?(@.code=='FIN')].id");
    assertThat(finIds).isNotEmpty();
    Number finId = (Number) finIds.get(0);

    mvc.perform(
            post("/api/forms/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "formCode":"leave",
                      "userId":"alice",
                      "orgUnitId":%s,
                      "payloadJson":"{\\"days\\":2,\\"reason\\":\\"demo\\",\\"amount\\":3}"
                    }
                    """
                        .formatted(finId.longValue())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"));

    MvcResult inbox =
        mvc.perform(get("/api/self/inbox").param("userId", "manager1"))
            .andExpect(status().isOk())
            .andReturn();
    Integer taskId =
        JsonPath.read(inbox.getResponse().getContentAsString(), "$[0].id");

    mvc.perform(
            post("/api/workflow/tasks/" + taskId + "/action")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":"manager1","action":"APPROVE","remarks":"ok"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("APPROVE"));

    mvc.perform(get("/api/self/mine").param("userId", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("APPROVED"));
  }
}
