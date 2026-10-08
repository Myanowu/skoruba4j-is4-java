package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/** Sends HTML clients to {@code /login?ReturnUrl=/connect/authorize...}. */
public final class Is4LoginAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {
  public Is4LoginAuthenticationEntryPoint() {
    super("/login");
  }

  @Override
  protected String determineUrlToUseForThisRequest(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
    return Is4ReturnUrls.loginRedirect(request);
  }
}
