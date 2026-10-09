package com.myano.skoruba4j.protocol;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Drops a restored STS login whose user id is not in the current database. The auth cookie
 * survives a database switch and would otherwise keep issuing tokens for a missing user.
 */
public final class CurrentIdentityUserFilter extends OncePerRequestFilter {
  private final IdentityUserPresence users;
  private final SecurityContextRepository contexts;

  public CurrentIdentityUserFilter(IdentityUserPresence users, SecurityContextRepository contexts) {
    this.users = users;
    this.contexts = contexts;
  }

  /**
   * Anonymous requests pass through. A signed-in id missing from {@code Users} clears the security
   * context and the auth cookie before authorize can use it.
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (users != null
        && users.databaseConfigured()
        && signedIn(auth)
        && !users.exists(auth.getName())) {
      SecurityContext empty = SecurityContextHolder.createEmptyContext();
      SecurityContextHolder.setContext(empty);
      contexts.saveContext(empty, request, response);
    }
    filterChain.doFilter(request, response);
  }

  private static boolean signedIn(Authentication auth) {
    return auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)
        && auth.getName() != null
        && !auth.getName().isBlank();
  }
}
