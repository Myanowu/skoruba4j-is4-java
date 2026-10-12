package com.myano.orgplatform.web;

import com.myano.orgplatform.config.OrgPlatformProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

  private final OrgPlatformProperties properties;

  public SessionController(OrgPlatformProperties properties) {
    this.properties = properties;
  }

  /** Who is signed in. Open mode has no STS user. */
  @GetMapping("/api/me")
  public SessionView me(Authentication authentication) {
    if (properties.getSecurity().isOpen()
        || authentication == null
        || !authentication.isAuthenticated()) {
      return new SessionView(true, false, "", "");
    }
    return new SessionView(false, true, displayName(authentication), subject(authentication));
  }

  /** Login name from the ID token. Falls back to the STS user id. */
  private static String displayName(Authentication authentication) {
    if (authentication.getPrincipal() instanceof OidcUser oidc) {
      String preferred = oidc.getPreferredUsername();
      if (preferred != null && !preferred.isBlank()) {
        return preferred;
      }
      if (oidc.getSubject() != null && !oidc.getSubject().isBlank()) {
        return oidc.getSubject();
      }
    }
    return authentication.getName() == null ? "" : authentication.getName();
  }

  /** STS {@code Users.Id} ({@code sub}). */
  private static String subject(Authentication authentication) {
    if (authentication.getPrincipal() instanceof OidcUser oidc && oidc.getSubject() != null) {
      return oidc.getSubject();
    }
    return "";
  }

  public record SessionView(boolean open, boolean authenticated, String name, String sub) {}
}
