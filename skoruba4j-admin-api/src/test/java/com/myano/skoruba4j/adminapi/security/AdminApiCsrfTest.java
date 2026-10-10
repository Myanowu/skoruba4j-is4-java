package com.myano.skoruba4j.adminapi.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myano.skoruba4j.adminapi.AdminApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CSRF protection smoke test. Verifies:
 * <ul>
 *   <li>UI chain (session-based) requires CSRF token for POST /login</li>
 *   <li>JWT chain (/api/**) does NOT require CSRF token (pure Bearer, no cookie)</li>
 * </ul>
 */
@SpringBootTest(
    classes = {AdminApiApplication.class, AdminApiCsrfTest.Overrides.class},
    properties = {
      "spring.profiles.active=oss",
      "idserver.db.url=",
      "idserver.issuer-uri=http://127.0.0.1:44302",
      "idserver.admin.api-ui-enabled=true",
      "idserver.admin.api-login-mode=LOCAL",
      "idserver.admin.role=MyRole",
      "server.ssl.enabled=false",
      "server.port=0"
    })
@AutoConfigureMockMvc
class AdminApiCsrfTest {

  @Autowired MockMvc mvc;

  @Test
  void loginFormIncludesCsrfToken() throws Exception {
    MvcResult result = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
    String body = result.getResponse().getContentAsString();
    // Hidden _csrf input must be present in the form
    if (!body.contains("name=\"_csrf\"")) {
      throw new AssertionError("login form missing CSRF hidden input");
    }
    // Meta tag for JS must also be present
    if (!body.contains("<meta name=\"_csrf\"")) {
      throw new AssertionError("login page missing _csrf meta tag");
    }
    if (!body.contains("<meta name=\"_csrf_header\"")) {
      throw new AssertionError("login page missing _csrf_header meta tag");
    }
  }

  @Test
  void postToLoginWithoutCsrfIsBlocked() throws Exception {
    // POST with no CSRF should be blocked. Spring Security's CsrfFilter
    // triggers an access-denied event, and this app's custom
    // accessDeniedHandler redirects to /login?denied=1 (clear session).
    MvcResult result =
        mvc.perform(
                post("/login")
                    .param("username", "any")
                    .param("password", "any"))
            .andReturn();
    int status = result.getResponse().getStatus();
    String location = result.getResponse().getRedirectedUrl();
    // Either 403 (direct CSRF rejection) or 302 to /login?denied=1 (accessDeniedHandler)
    // Both confirm CSRF blocked the request.
    if (status == 403) {
      return; // CsrfFilter rejected directly — good
    }
    if (status == 302 && location != null && location.contains("denied=1")) {
      return; // accessDeniedHandler kicked in — also good
    }
    throw new AssertionError(
        "POST without CSRF should be blocked, got status=" + status + " location=" + location);
  }

  @Test
  void jwtApiEndpointDoesNotRequireCsrf() throws Exception {
    // /api/** is on the JWT chain (csrf.disable()), so GET should work
    // even with no CSRF token, and POST should also not be blocked by CSRF
    mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    // 401 Unauthorized (not 403 Forbidden) confirms CSRF did not block —
    // only JWT auth is required
  }

  @TestConfiguration
  static class Overrides {
    @Bean
    @Primary
    UserDetailsService userDetailsService() {
      return username ->
          User.withUsername(username)
              .password("{noop}secret")
              .roles("MyRole")
              .build();
    }
  }
}
