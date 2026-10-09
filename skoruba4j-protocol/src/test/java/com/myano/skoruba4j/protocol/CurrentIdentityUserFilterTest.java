package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

class CurrentIdentityUserFilterTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void missingUserClearsAuthCookie() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "421f05d3-9496-4c93-b111-fa07a839f912",
                "N/A",
                AuthorityUtils.createAuthorityList("Admin")));
    AtomicBoolean savedEmpty = new AtomicBoolean();
    CurrentIdentityUserFilter filter =
        new CurrentIdentityUserFilter(presence(false), recordingRepository(savedEmpty));
    FilterChain chain =
        (request, response) -> assertFalse(signedIn(), "authorize must not see the dropped user");

    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertTrue(savedEmpty.get());
    assertFalse(signedIn());
  }

  @Test
  void existingUserStaysSignedIn() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "user-1", "N/A", AuthorityUtils.createAuthorityList("Admin")));
    AtomicBoolean saved = new AtomicBoolean();
    CurrentIdentityUserFilter filter =
        new CurrentIdentityUserFilter(presence(true), recordingRepository(saved));

    filter.doFilter(
        new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {});

    assertTrue(signedIn());
    assertFalse(saved.get());
  }

  private static IdentityUserPresence presence(boolean exists) {
    return new IdentityUserPresence() {
      @Override
      public boolean databaseConfigured() {
        return true;
      }

      @Override
      public boolean exists(String userId) {
        return exists;
      }
    };
  }

  private static boolean signedIn() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated();
  }

  private static SecurityContextRepository recordingRepository(AtomicBoolean savedEmpty) {
    return new SecurityContextRepository() {
      @Override
      public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        return SecurityContextHolder.createEmptyContext();
      }

      @Override
      public void saveContext(
          SecurityContext context,
          jakarta.servlet.http.HttpServletRequest request,
          jakarta.servlet.http.HttpServletResponse response) {
        savedEmpty.set(context.getAuthentication() == null);
      }

      @Override
      public boolean containsContext(jakarta.servlet.http.HttpServletRequest request) {
        return false;
      }
    };
  }
}
