package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class RpResumeTest {

  @Test
  void storesClientIdAndResolvesRedirectOrigin() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSecure(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    RpResume.store(request, response, "MyClientId");
    request.setCookies(response.getCookies());
    assertEquals("MyClientId", RpResume.read(request));
    RegisteredClient client =
        RegisteredClient.withId("1")
            .clientId("MyClientId")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://localhost:6061/signin-oidc")
            .build();
    assertEquals("https://localhost:6061/", RpResume.target(client));
    assertEquals(
        "https://localhost:6061/",
        RpResume.targetForClientIds(
            new InMemoryRegisteredClientRepository(client), "missing", "MyClientId"));
    assertNull(RpResume.targetForClientIds(null, "MyClientId"));
    MockHttpServletResponse clear = new MockHttpServletResponse();
    RpResume.clear(clear);
    assertEquals(0, clear.getCookie(RpResume.COOKIE).getMaxAge());
    assertNull(RpResume.target(null));
  }
}
