package com.myano.skoruba4j.protocol;

import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Authenticates public clients ({@link ClientAuthenticationMethod#NONE}) for password, delegation,
 * and refresh grants without PKCE.
 *
 * <p>SAS {@code PublicClientAuthenticationProvider} always calls {@code
 * CodeVerifierAuthenticator.authenticateRequired}. For non-authorization_code grants that method
 * returns false and then throws {@code invalid_grant} on {@code code_verifier} — so ROPC never
 * reaches user password verification. This provider short-circuits those grants.
 */
public final class Is4PublicClientAuthenticationProvider implements AuthenticationProvider {
  private final RegisteredClientRepository registeredClientRepository;

  public Is4PublicClientAuthenticationProvider(
      RegisteredClientRepository registeredClientRepository) {
    this.registeredClientRepository = registeredClientRepository;
  }

  /**
   * Returns an authenticated client token for password, delegation, and refresh public clients;
   * otherwise {@code null} so SAS PKCE handling stays for authorization_code.
   */
  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    OAuth2ClientAuthenticationToken clientAuthentication =
        (OAuth2ClientAuthenticationToken) authentication;
    if (!ClientAuthenticationMethod.NONE.equals(
        clientAuthentication.getClientAuthenticationMethod())) {
      return null;
    }
    Map<String, Object> parameters = clientAuthentication.getAdditionalParameters();
    Object grant = parameters == null ? null : parameters.get(OAuth2ParameterNames.GRANT_TYPE);
    String grantType = grant instanceof String s ? s : null;
    if (!Is4PublicClientAuthenticationConverter.supportsGrant(grantType)) {
      return null;
    }
    String clientId = clientAuthentication.getPrincipal().toString();
    RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
    if (registeredClient == null) {
      throwInvalidClient(OAuth2ParameterNames.CLIENT_ID);
    }
    if (!registeredClient
        .getClientAuthenticationMethods()
        .contains(clientAuthentication.getClientAuthenticationMethod())) {
      throwInvalidClient("authentication_method");
    }
    return new OAuth2ClientAuthenticationToken(
        registeredClient, clientAuthentication.getClientAuthenticationMethod(), null);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
  }

  private static void throwInvalidClient(String detail) {
    throw new OAuth2AuthenticationException(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_CLIENT, "Client authentication failed: " + detail, null));
  }
}
