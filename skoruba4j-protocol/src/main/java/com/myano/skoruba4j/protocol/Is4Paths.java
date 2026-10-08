package com.myano.skoruba4j.protocol;

/** IdentityServer4 route names advertised in discovery. */
public final class Is4Paths {
  public static final String AUTHORIZE = "/connect/authorize";
  public static final String TOKEN = "/connect/token";
  public static final String USERINFO = "/connect/userinfo";
  public static final String INTROSPECT = "/connect/introspect";
  public static final String REVOCATION = "/connect/revocation";
  public static final String END_SESSION = "/connect/endsession";
  public static final String DEVICE_AUTHORIZATION = "/connect/deviceauthorization";
  public static final String JWKS = "/.well-known/openid-configuration/jwks";
  public static final String OAUTH2_JWKS = "/oauth2/jwks";
  public static final String DISCOVERY = "/.well-known/openid-configuration";

  private Is4Paths() {}
}
