package com.myano.skoruba4j.protocol;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

/**
 * IdentityServer4 skips secret validation when {@code Clients.RequireClientSecret} is false, even
 * if the token request still posts {@code client_secret} or uses HTTP Basic.
 *
 * <p>Spring Authorization Server's {@code ClientSecretPostAuthenticationConverter} runs before the
 * public-client converter and then rejects method {@code none} with {@code invalid_client}. This
 * converter claims those requests as {@link ClientAuthenticationMethod#NONE} so PKCE (authorization
 * code) or {@link Is4PublicClientAuthenticationProvider} (refresh / password) can continue.
 */
public final class Is4PublicClientSecretOptionalConverter implements AuthenticationConverter {
  private final RegisteredClientRepository registeredClientRepository;

  public Is4PublicClientSecretOptionalConverter(
      RegisteredClientRepository registeredClientRepository) {
    this.registeredClientRepository = registeredClientRepository;
  }

  /**
   * Returns a public-client token when a secret was presented for a client that does not require
   * one; otherwise {@code null} so confidential-client converters still run.
   */
  @Override
  @Nullable
  public Authentication convert(HttpServletRequest request) {
    String[] basic = basicCredentials(request);
    String clientSecret = request.getParameter(OAuth2ParameterNames.CLIENT_SECRET);
    boolean secretPresented = StringUtils.hasText(clientSecret) || basic != null;
    if (!secretPresented) {
      return null;
    }
    String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
    if (!StringUtils.hasText(clientId) && basic != null) {
      clientId = basic[0];
    }
    if (!StringUtils.hasText(clientId)) {
      return null;
    }
    clientId = clientId.trim();
    RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
    if (registeredClient == null || !isPublic(registeredClient)) {
      return null;
    }
    Map<String, Object> additional = new HashMap<>();
    request
        .getParameterMap()
        .forEach(
            (key, values) -> {
              if (OAuth2ParameterNames.CLIENT_ID.equals(key)
                  || OAuth2ParameterNames.CLIENT_SECRET.equals(key)
                  || values == null
                  || values.length == 0) {
                return;
              }
              additional.put(key, values.length == 1 ? values[0] : values);
            });
    return new OAuth2ClientAuthenticationToken(
        clientId, ClientAuthenticationMethod.NONE, null, additional);
  }

  /** True when the IS4 client is public ({@code RequireClientSecret = false}). */
  static boolean isPublic(RegisteredClient registeredClient) {
    return registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
        && !registeredClient
            .getClientAuthenticationMethods()
            .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        && !registeredClient
            .getClientAuthenticationMethods()
            .contains(ClientAuthenticationMethod.CLIENT_SECRET_POST);
  }

  /**
   * Decodes {@code Authorization: Basic} per RFC 6749. Returns {@code [clientId, secret]}, or
   * {@code null} when the header is absent or not Basic.
   */
  @Nullable
  private static String[] basicCredentials(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (!StringUtils.hasText(header) || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
      return null;
    }
    try {
      String token =
          new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
      int split = token.indexOf(':');
      if (split <= 0) {
        return null;
      }
      String clientId = UriUtils.decode(token.substring(0, split), StandardCharsets.UTF_8);
      String secret = UriUtils.decode(token.substring(split + 1), StandardCharsets.UTF_8);
      if (!StringUtils.hasText(clientId)) {
        return null;
      }
      return new String[] {clientId, secret};
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
