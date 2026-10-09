package com.myano.skoruba4j.domain.identity;

/**
 * ASP.NET Identity authenticator token names ({@code UserTokens} / {@code AspNetUserTokens}).
 *
 * <p>Matches {@code UserStoreBase} internal provider {@code [AspNetUserStore]} and token name
 * {@code AuthenticatorKey}.
 */
public final class IdentityAuthenticator {
  public static final String LOGIN_PROVIDER = "[AspNetUserStore]";
  public static final String AUTHENTICATOR_KEY_NAME = "AuthenticatorKey";
  public static final String RECOVERY_CODES_NAME = "RecoveryCodes";

  private IdentityAuthenticator() {}
}
