package com.myano.orgplatform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "orgplatform.security.mode=oidc")
@AutoConfigureMockMvc
class AdminGateTest {

  static Path tempDir;

  @BeforeAll
  static void temp() throws Exception {
    tempDir = Files.createTempDirectory("orgplatform-gate");
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("orgplatform.data-dir", () -> tempDir.toString());
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("gate.sqlite"));
  }

  @Autowired MockMvc mvc;

  @Test
  void healthStaysOpenAndTheConsoleRequiresAnAdminRole() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isOk());

    mvc.perform(get("/api/org/directory"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("請先登入"));

    mvc.perform(get("/"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrlPattern("**/oauth2/authorization/sts"));

    mvc.perform(get("/api/org/directory").with(user("staff").authorities(new SimpleGrantedAuthority("User"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("需要 STS 管理角色"));

    mvc.perform(get("/").with(user("staff").authorities(new SimpleGrantedAuthority("User"))))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login/denied"));

    mvc.perform(
            get("/api/org/directory").with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_MyRole"))))
        .andExpect(status().isOk());

    mvc.perform(get("/api/me").with(user("admin").authorities(new SimpleGrantedAuthority("MyRole"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.name").value("admin"))
        .andExpect(jsonPath("$.open").value(false));
  }
}
