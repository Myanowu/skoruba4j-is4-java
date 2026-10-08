package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;

class EncryptedCookieSecurityContextRepositoryTest {

  @Test
  void cookieSurvivesNewRepositoryInstance(@TempDir Path dir) {
    AuthCookieCipher cipher = AuthCookieCipher.loadOrCreate(dir.resolve("sts-auth.key"));
    EncryptedCookieSecurityContextRepository writer =
        new EncryptedCookieSecurityContextRepository(
            cipher, "SKORUBA4J_STS_AUTH", Duration.ofHours(10), false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    var auth =
        UsernamePasswordAuthenticationToken.authenticated(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("User")));
    SecurityContextImpl context = new SecurityContextImpl(auth);
    writer.saveContext(context, request, response);

    String setCookie = response.getHeader("Set-Cookie");
    assertNotNull(setCookie);
    assertTrue(setCookie.startsWith("SKORUBA4J_STS_AUTH="));

    String value = setCookie.substring("SKORUBA4J_STS_AUTH=".length(), setCookie.indexOf(';'));
    MockHttpServletRequest next = new MockHttpServletRequest();
    next.setCookies(new jakarta.servlet.http.Cookie("SKORUBA4J_STS_AUTH", value));
    EncryptedCookieSecurityContextRepository reader =
        new EncryptedCookieSecurityContextRepository(
            AuthCookieCipher.loadOrCreate(dir.resolve("sts-auth.key")),
            "SKORUBA4J_STS_AUTH",
            Duration.ofHours(10),
            false);
    var loaded = reader.loadDeferredContext(next).get().getAuthentication();
    assertNotNull(loaded);
    assertEquals("alice", loaded.getName());
  }
}
