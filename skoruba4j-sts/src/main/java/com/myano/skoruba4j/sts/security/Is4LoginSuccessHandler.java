package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.protocol.AccountChooser;
import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import com.myano.skoruba4j.protocol.RpResume;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * Resume {@code /connect/authorize} after login (IS4 ReturnUrl). Fresh login skips the account
 * chooser (marks the ReturnUrl approved). Direct STS login stays on {@code /}.
 */
public final class Is4LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
  private final SecurityContextRepository securityContextRepository;
  private RequestCache requestCache;

  public Is4LoginSuccessHandler() {
    this(new HttpSessionSecurityContextRepository());
  }

  public Is4LoginSuccessHandler(SecurityContextRepository securityContextRepository) {
    this.securityContextRepository =
        securityContextRepository == null
            ? new HttpSessionSecurityContextRepository()
            : securityContextRepository;
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
    RpResume.clear(response);
    return "/";
  }
}
