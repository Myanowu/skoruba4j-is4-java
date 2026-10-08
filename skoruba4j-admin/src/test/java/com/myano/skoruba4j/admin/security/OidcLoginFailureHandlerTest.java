package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OidcLoginFailureHandlerTest {

  @Test
  void sendsBrowserToLoginPageInsteadOfRestartingAuthorize() throws Exception {
    String[] location = new String[1];
    HttpServletResponse response =
        (HttpServletResponse)
            Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                  if ("sendRedirect".equals(method.getName())) {
                    location[0] = (String) args[0];
                    return null;
                  }
                  if (method.getReturnType() == boolean.class) {
                    return false;
                  }
                  if (method.getReturnType() == int.class) {
                    return 0;
                  }
                  return null;
                });
    HttpServletRequest request =
        (HttpServletRequest)
            Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> {
                  if (method.getReturnType() == boolean.class) {
                    return false;
                  }
                  if (method.getReturnType() == int.class) {
                    return 0;
                  }
                  return null;
                });
    new OidcLoginFailureHandler()
        .onAuthenticationFailure(
            request,
            response,
            new OAuth2AuthenticationException(
                new OAuth2Error("invalid_scope", "OAuth 2.0 Parameter: scope", null)));
    assertTrue(location[0].startsWith("/login?"));
    assertTrue(location[0].contains("error=invalid_scope"));
    assertTrue(!location[0].contains("/oauth2/authorization"));
  }
}
