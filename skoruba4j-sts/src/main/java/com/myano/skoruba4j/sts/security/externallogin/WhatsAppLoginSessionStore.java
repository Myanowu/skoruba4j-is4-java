package com.myano.skoruba4j.sts.security.externallogin;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Short-lived WhatsApp QR login sessions (single-node; not shared across STS instances). */
@Component
public final class WhatsAppLoginSessionStore {
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
  private static final int CODE_LEN = 8;

  private final ConcurrentHashMap<String, WhatsAppLoginSession> sessions = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  public WhatsAppLoginSession create(String clientId) {
    purgeExpired();
    Instant now = Instant.now();
    String code = newCode();
    WhatsAppLoginSession session =
        new WhatsAppLoginSession(code, clientId, now, now.plus(TTL));
    sessions.put(code, session);
    return session;
  }

  public Optional<WhatsAppLoginSession> find(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    WhatsAppLoginSession session = sessions.get(code.trim().toUpperCase());
    if (session == null) {
      return Optional.empty();
    }
    if (session.expired(Instant.now())) {
      sessions.remove(session.code());
      return Optional.empty();
    }
    return Optional.of(session);
  }

  public Optional<WhatsAppLoginSession> findByLoginMessage(String messageBody) {
    return WhatsAppLoginMessages.extractCode(messageBody).flatMap(this::find);
  }

  public void remove(String code) {
    if (code != null) {
      sessions.remove(code.trim().toUpperCase());
    }
  }

  private String newCode() {
    for (int attempt = 0; attempt < 20; attempt++) {
      char[] buf = new char[CODE_LEN];
      for (int i = 0; i < CODE_LEN; i++) {
        buf[i] = CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)];
      }
      String code = new String(buf);
      if (!sessions.containsKey(code)) {
        return code;
      }
    }
    return Long.toHexString(random.nextLong()).toUpperCase().substring(0, CODE_LEN);
  }

  private void purgeExpired() {
    Instant now = Instant.now();
    Iterator<Map.Entry<String, WhatsAppLoginSession>> it = sessions.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, WhatsAppLoginSession> e = it.next();
      if (e.getValue().expired(now) || e.getValue().status() == WhatsAppLoginSession.Status.CONSUMED) {
        it.remove();
      }
    }
  }
}
