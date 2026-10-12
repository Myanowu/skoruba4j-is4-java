package com.myano.orgplatform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthTest {

  static Path tempDir;

  @BeforeAll
  static void temp() throws Exception {
    tempDir = Files.createTempDirectory("orgplatform-test");
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("orgplatform.data-dir", () -> tempDir.toString());
    registry.add(
        "spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.sqlite"));
  }

  @Autowired MockMvc mvc;

  @Test
  void healthUp() throws Exception {
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
