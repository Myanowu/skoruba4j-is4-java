package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BrowserAuthorizeBearerTokenResolverTest {

  @Test
  void authorizeIgnoresBearerSoSessionCanProceed() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", Is4Paths.AUTHORIZE);
    request.setRequestURI(Is4Paths.AUTHORIZE);
    request.addHeader("Authorization", "Bearer abc");
    assertNull(new BrowserAuthorizeBearerTokenResolver().resolve(request));
  }

  @Test
  void userinfoStillReadsBearer() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", Is4Paths.USERINFO);
    request.setRequestURI(Is4Paths.USERINFO);
    request.addHeader("Authorization", "Bearer abc");
    assertEquals("abc", new BrowserAuthorizeBearerTokenResolver().resolve(request));
  }
}
