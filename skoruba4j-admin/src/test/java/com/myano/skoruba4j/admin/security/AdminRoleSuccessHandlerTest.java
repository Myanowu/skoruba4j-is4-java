package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminRoleSuccessHandlerTest {

  @AfterEach
  void clearUser() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void missingAdminRoleReturnsToLoginAndClearsTheSession() throws Exception {
    String[] location = new String[1];
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            "ada", "n/a", List.of(new SimpleGrantedAuthority("User")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    new AdminRoleSuccessHandler("Admin")
        .onAuthenticationSuccess(request(), response(location), auth);

    assertTrue(location[0].startsWith("/login?denied=1&need=Admin&had="));
    assertTrue(location[0].contains("had=User"));
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void springPrefixedRoleContinuesToAdmin() throws Exception {
    String[] location = new String[1];
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            "ada", "n/a", List.of(new SimpleGrantedAuthority("ROLE_MyRole")));

    new AdminRoleSuccessHandler("MyRole")
        .onAuthenticationSuccess(request(), response(location), auth);

    assertEquals("/admin", location[0]);
  }

  @Test
  void adminRoleContinuesToAdmin() throws Exception {
    String[] location = new String[1];
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            "ada", "n/a", List.of(new SimpleGrantedAuthority("Admin")));

    new AdminRoleSuccessHandler("Admin")
        .onAuthenticationSuccess(request(), response(location), auth);

    assertEquals("/admin", location[0]);
  }

  private static HttpServletRequest request() {
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getContextPath" -> "";
                  case "getSession" -> null;
                  default -> {
                    if (method.getReturnType() == boolean.class) {
                      yield false;
                    }
                    if (method.getReturnType() == int.class) {
                      yield 0;
                    }
                    yield null;
                  }
                });
  }

  private static HttpServletResponse response(String[] location) {
    return (HttpServletResponse)
        Proxy.newProxyInstance(
            HttpServletResponse.class.getClassLoader(),
            new Class<?>[] {HttpServletResponse.class},
            (proxy, method, args) -> {
              if ("sendRedirect".equals(method.getName())) {
                location[0] = (String) args[0];
                return null;
              }
              if ("encodeRedirectURL".equals(method.getName())
                  || "encodeURL".equals(method.getName())) {
                return args[0];
              }
              if (method.getReturnType() == boolean.class) {
                return false;
              }
              if (method.getReturnType() == int.class) {
                return 0;
              }
              return null;
            });
  }
}
