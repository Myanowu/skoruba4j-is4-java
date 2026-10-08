package com.myano.skoruba4j.protocol;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * For browser {@code /connect/authorize}, send an authenticated user through {@code /login/choose}
 * once before issuing a code (unless {@code prompt=none}).
 */
public final class AccountChooserAuthorizeFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!shouldOfferChooser(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    String returnUrl = AccountChooser.currentAuthorizeReturnUrl(request);
    if (!Is4ReturnUrls.isSafe(returnUrl)) {
      filterChain.doFilter(request, response);
      return;
    }
    HttpSession session = request.getSession(false);
    if (AccountChooser.isApproved(session, returnUrl)) {
      AccountChooser.clearApproved(session);
      filterChain.doFilter(request, response);
      return;
    }
    response.sendRedirect(AccountChooser.chooseRedirect(returnUrl));
  }

  static boolean shouldOfferChooser(HttpServletRequest request) {
    if (!HttpMethod.GET.matches(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    if (path == null || !Is4Paths.AUTHORIZE.equals(path)) {
      return false;
    }
    String prompt = request.getParameter("prompt");
    if (AccountChooser.skipForPrompt(prompt)) {
      return false;
    }
    if (!looksLikeBrowser(request)) {
      return false;
    }
    return signedIn(currentAuthentication(request));
  }

  private static boolean signedIn(Authentication auth) {
    return auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken);
  }

  private static Authentication currentAuthentication(HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (signedIn(auth)) {
      return auth;
    }
    HttpSession session = request.getSession(false);
    if (session == null) {
      return auth;
    }
    Object raw =
        session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    if (raw instanceof SecurityContext context) {
      return context.getAuthentication();
    }
    return auth;
  }

  private static boolean looksLikeBrowser(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    if (accept == null || accept.isBlank()) {
      return true;
    }
    String a = accept.toLowerCase(Locale.ROOT);
    return a.contains(MediaType.TEXT_HTML_VALUE) || a.contains("*/*");
  }
}
