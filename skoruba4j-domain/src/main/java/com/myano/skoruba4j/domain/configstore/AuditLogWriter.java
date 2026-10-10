package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Best-effort {@code AuditLog} insert. Never throws to callers (missing table / JDBC errors are
 * logged at FINE level and counted via {@link AuditFailureMeter} but never propagated).
 */
public final class AuditLogWriter {
  private static final Logger LOG = Logger.getLogger(AuditLogWriter.class.getName());

  public static final String SOURCE_STS = "skoruba4j-sts";
  public static final String SOURCE_ADMIN = "skoruba4j-admin";

  public static final String CATEGORY_AUTHENTICATION = "Authentication";
  public static final String CATEGORY_TOKEN = "Token";
  public static final String CATEGORY_ADMIN = "Admin";

  public static final String EVENT_LOGIN_SUCCESS = "UserLoginSuccess";
  public static final String EVENT_LOGIN_FAILURE = "UserLoginFailure";
  public static final String EVENT_TOKEN_ISSUED = "TokenIssuedSuccess";
  public static final String EVENT_TOKEN_FAILURE = "TokenIssuedFailure";
  public static final String EVENT_ADMIN_REQUEST = "AdminRequestEvent";

  private AuditLogWriter() {}

  public static void write(
      Optional<JdbcRepositories> jdbc,
      String event,
      String source,
      String category,
      String subjectIdentifier,
      String subjectName,
      String subjectType,
      String action,
      String data) {
    if (jdbc == null || jdbc.isEmpty()) {
      return;
    }
    try {
      AuditLogRepository repo = jdbc.get().auditLogs();
      if (!repo.tableReady()) {
        return;
      }
      repo.insert(
          event,
          source,
          category,
          subjectIdentifier,
          subjectName,
          subjectType,
          "",
          action,
          data);
    } catch (RuntimeException ex) {
      // Audit must never break login / token / admin flows.
      LOG.log(Level.FINE, "audit insert failed (event={0}): {1}", new Object[] {event, ex.toString()});
      AuditFailureMeter.record(ex);
    }
  }

  public static void loginSuccess(Optional<JdbcRepositories> jdbc, String username) {
    String subject = blankToDash(username);
    write(
        jdbc,
        EVENT_LOGIN_SUCCESS,
        SOURCE_STS,
        CATEGORY_AUTHENTICATION,
        subject,
        subject,
        "User",
        "POST /login",
        "");
  }

  public static void loginFailure(
      Optional<JdbcRepositories> jdbc, String username, String reason) {
    String subject = blankToDash(username);
    write(
        jdbc,
        EVENT_LOGIN_FAILURE,
        SOURCE_STS,
        CATEGORY_AUTHENTICATION,
        subject,
        subject,
        "User",
        "POST /login",
        blankToDash(reason));
  }

  public static void tokenIssued(
      Optional<JdbcRepositories> jdbc, String subject, String clientId, String grantType) {
    write(
        jdbc,
        EVENT_TOKEN_ISSUED,
        SOURCE_STS,
        CATEGORY_TOKEN,
        blankToDash(subject),
        blankToDash(subject),
        "User",
        "POST /connect/token",
        "client_id="
            + blankToDash(clientId)
            + "; grant_type="
            + blankToDash(grantType));
  }

  public static void tokenFailure(
      Optional<JdbcRepositories> jdbc, String subject, String detail) {
    write(
        jdbc,
        EVENT_TOKEN_FAILURE,
        SOURCE_STS,
        CATEGORY_TOKEN,
        blankToDash(subject),
        blankToDash(subject),
        "User",
        "POST /connect/token",
        blankToDash(detail));
  }

  public static void adminRequest(
      Optional<JdbcRepositories> jdbc, String subject, String action) {
    write(
        jdbc,
        EVENT_ADMIN_REQUEST,
        SOURCE_ADMIN,
        CATEGORY_ADMIN,
        blankToDash(subject),
        blankToDash(subject),
        "User",
        blankToDash(action),
        "");
  }

  static String blankToDash(String value) {
    return value == null || value.isBlank() ? "-" : value.trim();
  }
}
