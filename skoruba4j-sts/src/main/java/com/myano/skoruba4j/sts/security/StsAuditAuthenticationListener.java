package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.domain.configstore.AuditLogWriter;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

/**
 * Writes {@code AuditLog} rows for token endpoint success/failure. Form login success/failure is
 * recorded in {@link FormLoginSecurityConfiguration} handlers so redirects stay unchanged.
 */
@Component
public class StsAuditAuthenticationListener {
  private final Optional<JdbcRepositories> jdbc;

  public StsAuditAuthenticationListener(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @EventListener
  public void onSuccess(AuthenticationSuccessEvent event) {
    Authentication auth = event.getAuthentication();
    if (!(auth instanceof OAuth2AccessTokenAuthenticationToken tokenAuth)) {
      return;
    }
    String clientId = clientId(tokenAuth);
    String subject = tokenAuth.getName();
    AuditLogWriter.tokenIssued(jdbc, subject, clientId, "-");
  }

  @EventListener
  public void onFailure(AbstractAuthenticationFailureEvent event) {
    Authentication auth = event.getAuthentication();
    if (auth instanceof UsernamePasswordAuthenticationToken) {
      return;
    }
    if (!isTokenRelated(auth)) {
      return;
    }
    String subject = auth == null || auth.getName() == null ? "-" : auth.getName();
    String detail =
        event.getException() == null
            ? (auth == null ? "-" : auth.getClass().getSimpleName())
            : event.getException().getClass().getSimpleName()
                + ": "
                + nullToEmpty(event.getException().getMessage());
    AuditLogWriter.tokenFailure(jdbc, subject, detail);
  }

  private static boolean isTokenRelated(Authentication auth) {
    if (auth == null) {
      return false;
    }
    return auth instanceof OAuth2AccessTokenAuthenticationToken
        || auth instanceof OAuth2ClientAuthenticationToken
        || auth.getClass().getName().contains("oauth2.server.authorization");
  }

  private static String clientId(OAuth2AccessTokenAuthenticationToken tokenAuth) {
    RegisteredClient registered = tokenAuth.getRegisteredClient();
    if (registered != null && registered.getClientId() != null) {
      return registered.getClientId();
    }
    Object principal = tokenAuth.getPrincipal();
    if (principal instanceof Authentication client && client.getName() != null) {
      return client.getName();
    }
    return "-";
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
