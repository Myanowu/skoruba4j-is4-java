package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * After password success: if {@code TwoFactorEnabled} and an authenticator key exist, park the user
 * id in session and send them to {@code /login/2fa}; otherwise complete normal login.
 */
public final class TwoFactorGateSuccessHandler implements AuthenticationSuccessHandler {
  private final Optional<JdbcRepositories> jdbc;
  private final SecurityContextRepository securityContextRepository;
  private final AuthenticationSuccessHandler afterTwoFactor;

  public TwoFactorGateSuccessHandler(
      Optional<JdbcRepositories> jdbc,
      SecurityContextRepository securityContextRepository,
      AuthenticationSuccessHandler afterTwoFactor) {
    this.jdbc = jdbc;
    this.securityContextRepository = securityContextRepository;
    this.afterTwoFactor = afterTwoFactor;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    String userId = authentication == null ? null : authentication.getName();
    if (requiresTwoFactor(userId)) {
      String returnUrl = request.getParameter("ReturnUrl");
      TwoFactorLogin.begin(
          request.getSession(true),
          userId,
          Is4ReturnUrls.isSafe(returnUrl) ? returnUrl : null);
      SecurityContextHolder.clearContext();
      securityContextRepository.saveContext(
          SecurityContextHolder.createEmptyContext(), request, response);
      String location = "/login/2fa";
      if (Is4ReturnUrls.isSafe(returnUrl)) {
        location =
            location
                + "?ReturnUrl="
                + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
      }
      response.sendRedirect(location);
      return;
    }
    afterTwoFactor.onAuthenticationSuccess(request, response, authentication);
  }

  private boolean requiresTwoFactor(String userId) {
    if (jdbc.isEmpty() || userId == null || userId.isBlank()) {
      return false;
    }
    Optional<IdentityUser> user = jdbc.get().users().findById(userId);
    if (user.isEmpty() || !user.get().twoFactorEnabled()) {
      return false;
    }
    return jdbc.get().users().findAuthenticatorKey(userId).isPresent();
  }
}
