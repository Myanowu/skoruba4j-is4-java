package com.myano.skoruba4j.sts.security.externallogin;

import com.myano.skoruba4j.domain.externallogin.ExternalLoginClientSettings;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginLinkMode;
import com.myano.skoruba4j.domain.externallogin.PhoneNumbers;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.UserProfileWrite;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/** Maps external OIDC / WhatsApp / WeChat identity onto local Users / UserLogins per Client link-mode. */
@Service
public final class ExternalIdentityLinker {
  public static final String GOOGLE = "google";
  public static final String MICROSOFT = "microsoft";
  public static final String WHATSAPP = "whatsapp";
  public static final String WECHAT = "wechat";

  private final Optional<JdbcRepositories> jdbc;
  private final IdentityPasswordHasher hasher;

  public ExternalIdentityLinker(Optional<JdbcRepositories> jdbc, IdentityPasswordHasher hasher) {
    this.jdbc = jdbc;
    this.hasher = hasher;
  }

  public ExternalLoginLinkResult linkGoogle(OidcUser oidcUser, ExternalLoginLinkMode mode) {
    return linkExternal(GOOGLE, oidcUser, mode);
  }

  public ExternalLoginLinkResult linkMicrosoft(OidcUser oidcUser, ExternalLoginLinkMode mode) {
    return linkExternal(MICROSOFT, oidcUser, mode);
  }

  /**
   * WhatsApp Cloud API inbound phone → local user. Provider key is digits-only (wa_id style).
   */
  public ExternalLoginLinkResult linkWhatsApp(String phoneRaw, ExternalLoginLinkMode mode) {
    if (jdbc.isEmpty()) {
      return new ExternalLoginLinkResult.Rejected("external-no-db");
    }
    String digits = PhoneNumbers.digitsOnly(phoneRaw);
    if (digits.isEmpty()) {
      return new ExternalLoginLinkResult.Rejected("external-no-phone");
    }
    String e164 = PhoneNumbers.toE164(digits);
    ExternalLoginLinkMode linkMode = mode == null ? ExternalLoginLinkMode.LINK_EXISTING : mode;
    var users = jdbc.get().users();
    Optional<IdentityUser> byLogin = users.findByLogin(WHATSAPP, digits);
    if (byLogin.isPresent()) {
      return new ExternalLoginLinkResult.Authenticated(byLogin.get());
    }
    Optional<IdentityUser> byPhone =
        users.findByPhoneNumber(e164).or(() -> users.findByPhoneNumber(digits));
    if (byPhone.isPresent()) {
      users.addLogin(byPhone.get().id(), WHATSAPP, digits, "WhatsApp");
      return new ExternalLoginLinkResult.Authenticated(byPhone.get());
    }
    String suggested = "wa" + (digits.length() > 12 ? digits.substring(digits.length() - 12) : digits);
    return switch (linkMode) {
      case LINK_EXISTING -> new ExternalLoginLinkResult.Rejected("external-no-account");
      case AUTO_CREATE -> {
        IdentityUser created = createUser(suggested, "");
        stampWhatsAppPhone(created.id(), e164);
        users.addLogin(created.id(), WHATSAPP, digits, "WhatsApp");
        yield new ExternalLoginLinkResult.Authenticated(
            users.findById(created.id()).orElse(created));
      }
      case CONFIRM ->
          new ExternalLoginLinkResult.NeedConfirm(
              new PendingExternalLogin(WHATSAPP, digits, "", suggested, "WhatsApp"));
    };
  }

  /**
   * WeChat Open Platform identity. Provider key prefers {@code unionid}, else {@code openid}.
   */
  public ExternalLoginLinkResult linkWeChat(
      String openId, String unionId, String nickname, ExternalLoginLinkMode mode) {
    if (jdbc.isEmpty()) {
      return new ExternalLoginLinkResult.Rejected("external-no-db");
    }
    String open = openId == null ? "" : openId.trim();
    String union = unionId == null ? "" : unionId.trim();
    if (open.isEmpty() && union.isEmpty()) {
      return new ExternalLoginLinkResult.Rejected("external-no-subject");
    }
    String providerKey = !union.isEmpty() ? union : open;
    String display =
        nickname == null || nickname.isBlank()
            ? "WeChat"
            : nickname.trim();
    ExternalLoginLinkMode linkMode = mode == null ? ExternalLoginLinkMode.LINK_EXISTING : mode;
    var users = jdbc.get().users();
    Optional<IdentityUser> byKey = users.findByLogin(WECHAT, providerKey);
    if (byKey.isPresent()) {
      return new ExternalLoginLinkResult.Authenticated(byKey.get());
    }
    if (!union.isEmpty() && !open.isEmpty() && !union.equals(open)) {
      Optional<IdentityUser> byOpenId = users.findByLogin(WECHAT, open);
      if (byOpenId.isPresent()) {
        users.addLogin(byOpenId.get().id(), WECHAT, providerKey, display);
        return new ExternalLoginLinkResult.Authenticated(byOpenId.get());
      }
    }
    String safeUser =
        "wx" + providerKey.replaceAll("[^a-zA-Z0-9]", "");
    if (safeUser.length() > 64) {
      safeUser = safeUser.substring(0, 64);
    }
    if (safeUser.length() < 3) {
      safeUser = "wxuser";
    }
    return switch (linkMode) {
      case LINK_EXISTING -> new ExternalLoginLinkResult.Rejected("external-no-account");
      case AUTO_CREATE -> {
        IdentityUser created = createUser(safeUser, "");
        users.addLogin(created.id(), WECHAT, providerKey, display);
        yield new ExternalLoginLinkResult.Authenticated(created);
      }
      case CONFIRM ->
          new ExternalLoginLinkResult.NeedConfirm(
              new PendingExternalLogin(WECHAT, providerKey, "", safeUser, display));
    };
  }

  public ExternalLoginLinkResult linkExternal(
      String provider, OidcUser oidcUser, ExternalLoginLinkMode mode) {
    if (jdbc.isEmpty()) {
      return new ExternalLoginLinkResult.Rejected("external-no-db");
    }
    if (provider == null || provider.isBlank()) {
      return new ExternalLoginLinkResult.Rejected("external");
    }
    if (oidcUser == null || oidcUser.getSubject() == null || oidcUser.getSubject().isBlank()) {
      return new ExternalLoginLinkResult.Rejected("external-no-subject");
    }
    ExternalLoginLinkMode linkMode = mode == null ? ExternalLoginLinkMode.LINK_EXISTING : mode;
    String loginProvider = provider.trim();
    String providerKey = oidcUser.getSubject().trim();
    String email = emailOf(oidcUser);
    String displayName = displayNameOf(oidcUser, loginProvider);
    String suggestedUser = suggestedUserName(oidcUser, email);

    var users = jdbc.get().users();
    Optional<IdentityUser> byLogin = users.findByLogin(loginProvider, providerKey);
    if (byLogin.isPresent()) {
      return new ExternalLoginLinkResult.Authenticated(byLogin.get());
    }

    if (email != null && !email.isBlank()) {
      Optional<IdentityUser> byEmail =
          users.findByNormalizedEmail(email.trim().toUpperCase(Locale.ROOT));
      if (byEmail.isPresent()) {
        users.addLogin(byEmail.get().id(), loginProvider, providerKey, displayName);
        return new ExternalLoginLinkResult.Authenticated(byEmail.get());
      }
    }

    return switch (linkMode) {
      case LINK_EXISTING -> new ExternalLoginLinkResult.Rejected("external-no-account");
      case AUTO_CREATE -> {
        if (email == null || email.isBlank()) {
          yield new ExternalLoginLinkResult.Rejected("external-no-email");
        }
        IdentityUser created = createUser(suggestedUser, email);
        users.addLogin(created.id(), loginProvider, providerKey, displayName);
        yield new ExternalLoginLinkResult.Authenticated(created);
      }
      case CONFIRM ->
          new ExternalLoginLinkResult.NeedConfirm(
              new PendingExternalLogin(
                  loginProvider, providerKey, email, suggestedUser, displayName));
    };
  }

  public ExternalLoginLinkResult completeConfirm(
      PendingExternalLogin pending, String userName, String email) {
    if (jdbc.isEmpty()) {
      return new ExternalLoginLinkResult.Rejected("external-no-db");
    }
    if (pending == null) {
      return new ExternalLoginLinkResult.Rejected("external-no-pending");
    }
    String mail = email == null || email.isBlank() ? pending.email() : email.trim();
    String name =
        userName == null || userName.isBlank()
            ? (pending.suggestedUserName() == null || pending.suggestedUserName().isBlank()
                ? suggestedUserName(null, mail)
                : pending.suggestedUserName().trim())
            : userName.trim();
    boolean whatsapp = WHATSAPP.equalsIgnoreCase(pending.loginProvider());
    boolean wechat = WECHAT.equalsIgnoreCase(pending.loginProvider());
    if ((mail == null || mail.isBlank()) && !whatsapp && !wechat) {
      return new ExternalLoginLinkResult.Rejected("external-no-email");
    }
    if (mail == null) {
      mail = "";
    }
    var users = jdbc.get().users();
    Optional<IdentityUser> byLogin =
        users.findByLogin(pending.loginProvider(), pending.providerKey());
    if (byLogin.isPresent()) {
      return new ExternalLoginLinkResult.Authenticated(byLogin.get());
    }
    if (!mail.isBlank()) {
      Optional<IdentityUser> byEmail = users.findByNormalizedEmail(mail.toUpperCase(Locale.ROOT));
      if (byEmail.isPresent()) {
        users.addLogin(
            byEmail.get().id(),
            pending.loginProvider(),
            pending.providerKey(),
            pending.displayName());
        return new ExternalLoginLinkResult.Authenticated(byEmail.get());
      }
    }
    if (whatsapp) {
      Optional<IdentityUser> byPhone = users.findByPhoneNumber(pending.providerKey());
      if (byPhone.isPresent()) {
        users.addLogin(
            byPhone.get().id(),
            pending.loginProvider(),
            pending.providerKey(),
            pending.displayName());
        return new ExternalLoginLinkResult.Authenticated(byPhone.get());
      }
    }
    if (users.findByNormalizedUserName(name.toUpperCase(Locale.ROOT)).isPresent()) {
      return new ExternalLoginLinkResult.Rejected("external-username-taken");
    }
    IdentityUser created = createUser(name, mail);
    if (whatsapp) {
      stampWhatsAppPhone(created.id(), PhoneNumbers.toE164(pending.providerKey()));
    }
    users.addLogin(
        created.id(), pending.loginProvider(), pending.providerKey(), pending.displayName());
    return new ExternalLoginLinkResult.Authenticated(
        users.findById(created.id()).orElse(created));
  }

  private void stampWhatsAppPhone(String userId, String e164) {
    if (jdbc.isEmpty() || userId == null || e164 == null || e164.isBlank()) {
      return;
    }
    Optional<IdentityUser> existing = jdbc.get().users().findById(userId);
    if (existing.isEmpty()) {
      return;
    }
    IdentityUser u = existing.get();
    jdbc.get()
        .users()
        .update(
            userId,
            new UserProfileWrite(
                u.userName(),
                u.email(),
                u.emailConfirmed(),
                e164,
                true,
                u.lockoutEnabled(),
                u.lockoutEnd(),
                u.accessFailedCount(),
                u.twoFactorEnabled()));
  }

  public ExternalLoginClientSettings settingsForClient(String clientId) {
    if (jdbc.isEmpty() || clientId == null || clientId.isBlank()) {
      return ExternalLoginClientSettings.disabled();
    }
    return jdbc.get()
        .clients()
        .findEnabledByClientId(clientId.trim())
        .map(ExternalLoginClientSettings::from)
        .orElseGet(ExternalLoginClientSettings::disabled);
  }

  private IdentityUser createUser(String userName, String email) {
    String name = uniqueUserName(userName, email);
    String id =
        jdbc.get()
            .users()
            .insert(name, email, true, hasher.hash(UUID.randomUUID().toString() + "!Aa1"));
    return jdbc.get()
        .users()
        .findById(id)
        .orElseThrow(() -> new IllegalStateException("created user missing"));
  }

  private String uniqueUserName(String suggested, String email) {
    String base = suggested;
    if (base == null || base.isBlank()) {
      base = suggestedUserName(null, email);
    }
    base = base.replaceAll("[^a-zA-Z0-9._-]", "");
    if (base.isBlank()) {
      base = "user";
    }
    if (base.length() > 64) {
      base = base.substring(0, 64);
    }
    var users = jdbc.get().users();
    if (users.findByNormalizedUserName(base.toUpperCase(Locale.ROOT)).isEmpty()) {
      return base;
    }
    for (int i = 2; i < 1000; i++) {
      String candidate = base + i;
      if (users.findByNormalizedUserName(candidate.toUpperCase(Locale.ROOT)).isEmpty()) {
        return candidate;
      }
    }
    return base + UUID.randomUUID().toString().substring(0, 8);
  }

  private static String emailOf(OidcUser user) {
    if (user.getEmail() != null && !user.getEmail().isBlank()) {
      return user.getEmail().trim();
    }
    Object claim = user.getClaims().get("email");
    if (claim != null && !String.valueOf(claim).isBlank()) {
      return String.valueOf(claim).trim();
    }
    Object upn = user.getClaims().get("preferred_username");
    if (upn != null) {
      String s = String.valueOf(upn).trim();
      if (s.contains("@")) {
        return s;
      }
    }
    return null;
  }

  private static String displayNameOf(OidcUser user, String provider) {
    if (user.getFullName() != null && !user.getFullName().isBlank()) {
      return user.getFullName().trim();
    }
    if (user.getPreferredUsername() != null && !user.getPreferredUsername().isBlank()) {
      return user.getPreferredUsername().trim();
    }
    return provider;
  }

  private static String suggestedUserName(OidcUser user, String email) {
    if (user != null
        && user.getPreferredUsername() != null
        && !user.getPreferredUsername().isBlank()) {
      String preferred = user.getPreferredUsername().trim();
      if (preferred.contains("@")) {
        return preferred.substring(0, preferred.indexOf('@'));
      }
      return preferred;
    }
    if (email != null && email.contains("@")) {
      return email.substring(0, email.indexOf('@'));
    }
    if (user != null && user.getGivenName() != null && !user.getGivenName().isBlank()) {
      return user.getGivenName().trim();
    }
    return "user";
  }
}
