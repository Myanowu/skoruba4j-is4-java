package com.myano.orgplatform;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
class OrgDirectoryTest {

  static Path tempDir;

  @BeforeAll
  static void temp() throws Exception {
    tempDir = Files.createTempDirectory("orgplatform-directory");
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("orgplatform.data-dir", () -> tempDir.toString());
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("directory.sqlite"));
  }

  @Autowired MockMvc mvc;

  @Test
  void sampleChartIsATreeWithPeople() throws Exception {
    mvc.perform(post("/api/org/demo/seed-chart")).andExpect(status().isOk());
    mvc.perform(post("/api/org/demo/seed-chart"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.units").value(5))
        .andExpect(jsonPath("$.people").value(7));

    MvcResult directory =
        mvc.perform(get("/api/org/directory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tree[0].code").value("HQ"))
            .andExpect(jsonPath("$.tree[0].name").value("總公司"))
            .andExpect(jsonPath("$.tree[0].directCount").value(1))
            .andExpect(jsonPath("$.tree[0].totalCount").value(7))
            .andExpect(jsonPath("$.tree[0].children[0].code").value("FIN"))
            .andExpect(jsonPath("$.people[?(@.employeeNo=='E001')].displayName", hasItem("周啟明")))
            .andReturn();

    Number finId =
        com.jayway.jsonpath.JsonPath.read(
            directory.getResponse().getContentAsString(), "$.tree[0].children[0].id");

    mvc.perform(get("/api/org/units/" + finId.longValue() + "/workspace"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.managerName").value("林知遠"))
        .andExpect(jsonPath("$.members[?(@.employeeNo=='A001')].roleName", hasItem("專員")));

    mvc.perform(
            post("/api/org/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"displayName":"測試員","employeeNo":"T001","email":"t@example.com","orgUnitId":%s,"primary":true}
                    """
                        .formatted(finId.longValue())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userKey").value("T001"))
        .andExpect(jsonPath("$.idpUserId").value(nullValue()));

    mvc.perform(
            post("/api/org/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"displayName":"重複","employeeNo":"T001","orgUnitId":%s}
                    """
                        .formatted(finId.longValue())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("工號已存在"));
  }
}
