package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.domain.configstore.AuditLogWriter;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.protocol.AccountChooser;
import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import com.myano.skoruba4j.protocol.RpResume;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * Resume {@code /connect/authorize} after login (IS4 ReturnUrl). Fresh login skips the account
 * chooser (marks the ReturnUrl approved). After RP logout without a saved authorize request, honour
 * {@link RpResume} so the browser returns to the RP {@code /login} instead of STS home.
 */
public final class Is4LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
  private final SecurityContextRepository securityContextRepository;
  private final RegisteredClientRepository clients;
  private final Optional<JdbcRepositories> jdbc;
  private RequestCache requestCache;

  public Is4LoginSuccessHandler() {
    this(new HttpSessionSecurityContextRepository(), null, Optional.empty());
  }

  public Is4LoginSuccessHandler(SecurityContextRepository securityContextRepository) {
    this(securityContextRepository, null, Optional.empty());
  }

  public Is4LoginSuccessHandler(
      SecurityContextRepository securityContextRepository, RegisteredClientRepository clients) {
    this(securityContextRepository, clients, Optional.empty());
  }

  public Is4LoginSuccessHandler(
      SecurityContextRepository securityContextRepository,
      RegisteredClientRepository clients,
      Optional<JdbcRepositories> jdbc) {
    this.securityContextRepository =
        securityContextRepository == null
            ? new HttpSessionSecurityContextRepository()
            : securityContextRepository;
    this.clients = clients;
    this.jdbc = jdbc == null ? Optional.empty() : jdbc;
    setDefaultTargetUrl("/");
    setAlwaysUseDefaultTargetUrl(false);
  }

  public void setRequestCache(RequestCache requestCache) {
    this.requestCache = requestCache;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    request.getSession(true);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
    AuditLogWriter.loginSuccess(
        jdbc, authentication == null ? null : authentication.getName());
    AccountChooser.markFreshLogin(request.getSession(true));
    String target = determineTargetUrl(request, response);
    if (Is4ReturnUrls.isSafe(target)) {
      AccountChooser.markApproved(request.getSession(true), target);
    }
    if (requestCache != null) {
      requestCache.removeRequest(request, response);
    }
    clearAuthenticationAttributes(request);
    getRedirectStrategy().sendRedirect(request, response, target);
  }

  @Override
  protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
    String returnUrl = request.getParameter("ReturnUrl");
    if (Is4ReturnUrls.isSafe(returnUrl)) {
      RpResume.clear(response);
      return returnUrl;
    }
    SavedRequest saved = requestCache == null ? null : requestCache.getRequest(request, response);
    if (saved != null && Is4ReturnUrls.isSafe(saved.getRedirectUrl())) {
      RpResume.clear(response);
      return saved.getRedirectUrl();
    }
    String resume = RpResume.targetForClientIds(clients, RpResume.read(request));
    RpResume.clear(response);
    if (resume != null) {
      return resume;
    }
    return "/";
  }
}
