package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.UserRepository;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

public final class PasswordResetService {
  public static final int MIN_PASSWORD_LENGTH = 6;

  private final Optional<JdbcRepositories> jdbc;
  private final PasswordResetTokens tokens;
  private final ResetMailSender mail;
  private final IdentityPasswordHasher hasher;

  public PasswordResetService(
      Optional<JdbcRepositories> jdbc,
      PasswordResetTokens tokens,
      ResetMailSender mail,
      IdentityPasswordHasher hasher) {
    this.jdbc = jdbc == null ? Optional.empty() : jdbc;
    this.tokens = tokens;
    this.mail = mail;
    this.hasher = hasher;
  }

  public void requestByEmail(String email, String resetUrlBase) {
    IdentityUser user = findByEmail(email);
    if (user == null || !user.emailConfirmed()) {
      return;
    }
    String to = DeliveryMailbox.to(user.email());
    if (to.isBlank() || resetUrlBase == null || resetUrlBase.isBlank()) {
      return;
    }
    String code = tokens.create(user);
    String url =
        resetUrlBase
            + (resetUrlBase.contains("?") ? "&" : "?")
            + "userId="
            + URLEncoder.encode(user.id(), StandardCharsets.UTF_8)
            + "&code="
            + URLEncoder.encode(code, StandardCharsets.UTF_8);
    String body = "Reset your password: " + url;
    mail.sendReset(to, "Reset password", body);
  }

  public String complete(String email, String password, String confirm, String code) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
    }
    if (confirm == null || !password.equals(confirm)) {
      return "Password and confirmation do not match.";
    }
    IdentityUser user = findByEmail(email);
    if (user == null) {
      return null;
    }
    if (!tokens.matches(user, code)) {
      return "Invalid or expired reset link.";
    }
    UserRepository users = jdbc.map(JdbcRepositories::users).orElse(null);
    if (users == null) {
      return "Reset is not available.";
    }
    users.setPasswordHash(user.id(), hasher.hash(password));
    return null;
  }

  private IdentityUser findByEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    String normalized = email.trim().toUpperCase(Locale.ROOT);
    return jdbc.flatMap(repos -> repos.users().findByNormalizedEmail(normalized)).orElse(null);
  }
}
