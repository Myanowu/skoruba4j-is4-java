package com.myano.skoruba4j.protocol;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

/**
 * IdentityServer4-style {@code /connect/endsession} used when {@code
 * idserver.logout.end-session=compatible}. Does not require the in-memory authorization store or an
 * {@code sid} claim. Set {@code strict} to keep Spring Authorization Server defaults.
 */
public final class Is4OidcLogoutAuthenticationProvider implements AuthenticationProvider {
  private final JwtDecoder jwtDecoder;
  private final RegisteredClientRepository clients;

  public Is4OidcLogoutAuthenticationProvider(
      JwtDecoder jwtDecoder, RegisteredClientRepository clients) {
    this.jwtDecoder = jwtDecoder;
    this.clients = clients;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    OidcLogoutAuthenticationToken request = (OidcLogoutAuthenticationToken) authentication;
    OidcIdToken idToken = readIdToken(request.getIdTokenHint());
    RegisteredClient client = resolveClient(request.getClientId(), idToken);
    String postLogout = request.getPostLogoutRedirectUri();
    if (!PostLogoutRedirects.allowed(client, postLogout)) {
      postLogout = null;
    }
    String clientId = client != null ? client.getClientId() : request.getClientId();
    return new OidcLogoutAuthenticationToken(
        idToken,
        (Authentication) request.getPrincipal(),
        request.getSessionId(),
        clientId,
        postLogout,
        request.getState());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return OidcLogoutAuthenticationToken.class.isAssignableFrom(authentication);
  }

  private OidcIdToken readIdToken(String hint) {
    if (hint == null || hint.isBlank()) {
      return anonymousToken("missing");
    }
    try {
      Jwt jwt = jwtDecoder.decode(hint);
      return new OidcIdToken(
          jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims());
    } catch (RuntimeException ignored) {
      return parseUnverified(hint);
    }
  }

  private static OidcIdToken parseUnverified(String hint) {
    try {
      JWT parsed = JWTParser.parse(hint);
      JWTClaimsSet set = parsed.getJWTClaimsSet();
      Instant iat = toInstant(set.getIssueTime(), Instant.now());
      Instant exp = toInstant(set.getExpirationTime(), iat.plusSeconds(60));
      Map<String, Object> claims = new HashMap<>();
      if (set.getSubject() != null) {
        claims.put("sub", set.getSubject());
      } else {
        claims.put("sub", "anonymous");
      }
      if (set.getIssuer() != null) {
        claims.put("iss", set.getIssuer().toString());
      }
      if (set.getAudience() != null && !set.getAudience().isEmpty()) {
        claims.put("aud", set.getAudience());
      }
      Object azp = set.getClaim("azp");
      if (azp != null) {
        claims.put("azp", azp);
      }
      return new OidcIdToken(hint, iat, exp, claims);
    } catch (Exception ignored) {
      return anonymousToken(hint);
    }
  }

  private RegisteredClient resolveClient(String requestedClientId, OidcIdToken idToken) {
    RegisteredClient fromRequest = findClient(requestedClientId);
    if (fromRequest != null) {
      return fromRequest;
    }
    if (idToken == null) {
      return null;
    }
    Object azp = idToken.getClaim("azp");
    if (azp instanceof String azpId) {
      RegisteredClient fromAzp = findClient(azpId);
      if (fromAzp != null) {
        return fromAzp;
      }
    }
    List<String> audience = idToken.getAudience();
    if (audience == null) {
      return null;
    }
    for (String aud : audience) {
      RegisteredClient fromAud = findClient(aud);
      if (fromAud != null) {
        return fromAud;
      }
    }
    return null;
  }

  private RegisteredClient findClient(String clientId) {
    if (clientId == null || clientId.isBlank() || clients == null) {
      return null;
    }
    return clients.findByClientId(clientId);
  }

  private static Instant toInstant(Date date, Instant fallback) {
    return date == null ? fallback : date.toInstant();
  }

  private static OidcIdToken anonymousToken(String hint) {
    Instant now = Instant.now();
    return new OidcIdToken(
        hint, now, now.plusSeconds(60), Map.of("sub", "anonymous"));
  }
}
