package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;

/** Maps ASP.NET Identity users onto OIDC profile/email claims. {@code sub} is {@code Users.Id}. */
public final class IdentityClaims {
  private IdentityClaims() {}

  public static Map<String, Object> claims(String userId, IdentityUser user, Set<String> scopes) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put(IdTokenClaimNames.SUB, userId);
    if (user == null || scopes == null) {
      return claims;
    }
    if (scopes.contains("profile")) {
      if (user.userName() != null && !user.userName().isBlank()) {
        claims.put(StandardClaimNames.PREFERRED_USERNAME, user.userName());
        claims.put(StandardClaimNames.NAME, user.userName());
      }
    }
    if (scopes.contains("email") && user.email() != null && !user.email().isBlank()) {
      claims.put(StandardClaimNames.EMAIL, user.email());
      claims.put(StandardClaimNames.EMAIL_VERIFIED, user.emailConfirmed());
    }
    return claims;
  }

  public static OidcUserInfo userInfo(String userId, IdentityUser user, Set<String> scopes) {
    return new OidcUserInfo(claims(userId, user, scopes));
  }

  public static List<String> roleNames(Collection<? extends GrantedAuthority> authorities) {
    if (authorities == null || authorities.isEmpty()) {
      return List.of();
    }
    return authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .filter(name -> name != null && !name.isBlank())
        .toList();
  }
}
