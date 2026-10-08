package com.myano.skoruba4j.protocol;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

public final class DelegationGrantAuthenticationToken
    extends OAuth2AuthorizationGrantAuthenticationToken {
  public static final AuthorizationGrantType DELEGATION = new AuthorizationGrantType("delegation");

  private final String subjectToken;

  public DelegationGrantAuthenticationToken(
      Authentication clientPrincipal, String subjectToken, Map<String, Object> additionalParameters) {
    super(DELEGATION, clientPrincipal, additionalParameters);
    this.subjectToken = subjectToken;
  }

  public String getSubjectToken() {
    return subjectToken;
  }
}
