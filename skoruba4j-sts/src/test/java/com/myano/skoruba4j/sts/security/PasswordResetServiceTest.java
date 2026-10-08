package com.myano.skoruba4j.sts.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.UserRepository;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PasswordResetServiceTest {

  @Test
  void deliveryMailboxStripsTagBeforeAt() {
    assertEquals("local@example.com", DeliveryMailbox.to("local--tag@example.com"));
    assertEquals("plain@example.com", DeliveryMailbox.to("plain@example.com"));
  }

  @Test
  void tokenMatchesUntilStampChanges() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-06T08:00:00Z"), ZoneOffset.UTC);
    PasswordResetTokens tokens =
        new PasswordResetTokens(PasswordResetTokens.keyFrom("test-key", "http://sts"), clock);
    IdentityUser user = user("id-1", "a@b.c", true, "stamp-1");
    String code = tokens.create(user);
    assertTrue(tokens.matches(user, code));
    assertFalse(tokens.matches(user("id-1", "a@b.c", true, "other"), code));
  }

  @Test
  void unknownOrUnconfirmedEmailDoesNotSend() {
    List<String> sent = new ArrayList<>();
    PasswordResetService service = service(user("id-1", "a@b.c", false, "s"), sent, null);
    service.requestByEmail("missing@b.c", "http://sts/reset-password");
    service.requestByEmail("a@b.c", "http://sts/reset-password");
    assertTrue(sent.isEmpty());
  }

  @Test
  void confirmedEmailSendsRewrittenAddress() {
    List<String> sent = new ArrayList<>();
    PasswordResetService service =
        service(user("id-1", "local--x@example.com", true, "s"), sent, null);
    service.requestByEmail("local--x@example.com", "http://sts/reset-password");
    assertEquals(1, sent.size());
    assertTrue(sent.get(0).startsWith("local@example.com "));
    assertTrue(sent.get(0).contains("http://sts/reset-password?"));
    assertTrue(sent.get(0).contains("code="));
  }

  @Test
  void completeUpdatesHashWhenTokenAndEmailMatch() {
    IdentityPasswordHasher hasher = new IdentityPasswordHasher();
    UserRepository users = Mockito.mock(UserRepository.class);
    IdentityUser user = user("id-1", "a@b.c", true, "stamp");
    Mockito.when(users.findByNormalizedEmail("A@B.C")).thenReturn(Optional.of(user));
    JdbcRepositories jdbc = Mockito.mock(JdbcRepositories.class);
    Mockito.when(jdbc.users()).thenReturn(users);
    PasswordResetTokens tokens =
        new PasswordResetTokens(
            PasswordResetTokens.keyFrom("test-key", "http://sts"), Clock.systemUTC());
    PasswordResetService service =
        new PasswordResetService(Optional.of(jdbc), tokens, (to, s, b) -> {}, hasher);
    String code = tokens.create(user);
    assertEquals(null, service.complete("a@b.c", "secret1", "secret1", code));
    Mockito.verify(users).setPasswordHash(Mockito.eq("id-1"), Mockito.anyString());
  }

  private static PasswordResetService service(
      IdentityUser stored, List<String> sent, IdentityPasswordHasher hasher) {
    UserRepository users = Mockito.mock(UserRepository.class);
    Mockito.when(users.findByNormalizedEmail(Mockito.anyString()))
        .thenAnswer(
            inv -> {
              String n = inv.getArgument(0);
              if (stored.normalizedEmail().equals(n)) {
                return Optional.of(stored);
              }
              return Optional.empty();
            });
    JdbcRepositories jdbc = Mockito.mock(JdbcRepositories.class);
    Mockito.when(jdbc.users()).thenReturn(users);
    PasswordResetTokens tokens =
        new PasswordResetTokens(
            PasswordResetTokens.keyFrom("test-key", "http://sts"), Clock.systemUTC());
    return new PasswordResetService(
        Optional.of(jdbc),
        tokens,
        (to, s, b) -> sent.add(to + " " + b),
        hasher == null ? new IdentityPasswordHasher() : hasher);
  }

  private static IdentityUser user(String id, String email, boolean confirmed, String stamp) {
    return new IdentityUser(
        id,
        "alice",
        "ALICE",
        email,
        email.toUpperCase(java.util.Locale.ROOT),
        confirmed,
        "hash",
        stamp,
        false,
        null,
        0,
        false,
        null,
        false);
  }
}
