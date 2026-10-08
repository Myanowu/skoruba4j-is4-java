package com.myano.skoruba4j.protocol;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Resource owner password credentials against the STS {@link AuthenticationManager} (Identity
 * users).
 */
public final class PasswordGrantAuthenticationProvider implements AuthenticationProvider {
  private static final OAuth2TokenType ID_TOKEN_TOKEN_TYPE =
      new OAuth2TokenType(OidcParameterNames.ID_TOKEN);

  private final AuthenticationManager authenticationManager;
  private final OAuth2AuthorizationService authorizationService;
  private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

  public PasswordGrantAuthenticationProvider(
      AuthenticationManager authenticationManager,
      OAuth2AuthorizationService authorizationService,
      OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
    this.authenticationManager = authenticationManager;
    this.authorizationService = authorizationService;
    this.tokenGenerator = tokenGenerator;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    PasswordGrantAuthenticationToken passwordGrant =
        (PasswordGrantAuthenticationToken) authentication;
    OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(passwordGrant);
    RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
    if (registeredClient == null
        || !registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.PASSWORD)) {
      throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
    }

    Authentication userAuth;
    try {
      // Same AuthenticationManager as STS /login form (Identity PBKDF2), not client-secret encoder.
      userAuth =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  passwordGrant.getUsername(), passwordGrant.getPassword()));
    } catch (AuthenticationException ex) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_GRANT,
              "invalid_username_or_password:" + ex.getClass().getSimpleName(),
              null));
    }
    if (userAuth == null || !userAuth.isAuthenticated()) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "invalid_username_or_password", null));
    }

    Set<String> authorizedScopes = resolveScopes(registeredClient, passwordGrant);
    String principalName = userAuth.getName();

    DefaultOAuth2TokenContext.Builder tokenContextBuilder =
        DefaultOAuth2TokenContext.builder()
            .registeredClient(registeredClient)
            .principal(userAuth)
            .authorizationServerContext(AuthorizationServerContextHolder.getContext())
            .authorizedScopes(authorizedScopes)
            .authorizationGrantType(AuthorizationGrantType.PASSWORD)
            .authorizationGrant(passwordGrant);

    OAuth2TokenContext accessContext =
        tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
    // JwtGenerator returns Jwt (ClaimAccessor), not OAuth2AccessToken — wrap like SAS code grant.
    OAuth2Token generatedAccessToken = tokenGenerator.generate(accessContext);
    if (generatedAccessToken == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "failed to generate access token", null));
    }
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            generatedAccessToken.getTokenValue(),
            generatedAccessToken.getIssuedAt(),
            generatedAccessToken.getExpiresAt(),
            authorizedScopes);

    OAuth2Authorization.Builder authorizationBuilder =
        OAuth2Authorization.withRegisteredClient(registeredClient)
            .principalName(principalName)
            .authorizationGrantType(AuthorizationGrantType.PASSWORD)
            .authorizedScopes(authorizedScopes)
            .attribute(Principal.class.getName(), userAuth);
    if (generatedAccessToken instanceof ClaimAccessor claimAccessor) {
      authorizationBuilder.token(
          accessToken,
          metadata ->
              metadata.put(
                  OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims()));
    } else {
      authorizationBuilder.accessToken(accessToken);
    }

    OAuth2RefreshToken refreshToken = null;
    if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)
        && authorizedScopes.contains("offline_access")) {
      OAuth2TokenContext refreshContext =
          tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
      OAuth2Token generatedRefresh = tokenGenerator.generate(refreshContext);
      if (generatedRefresh instanceof OAuth2RefreshToken generated) {
        refreshToken = generated;
        authorizationBuilder.refreshToken(refreshToken);
      }
    }

    OidcIdToken idToken = null;
    Map<String, Object> additionalParameters = new HashMap<>();
    if (authorizedScopes.contains(OidcScopes.OPENID)) {
      OAuth2TokenContext idContext =
          tokenContextBuilder
              .tokenType(ID_TOKEN_TOKEN_TYPE)
              .authorization(authorizationBuilder.build())
              .build();
      OAuth2Token generatedIdToken = tokenGenerator.generate(idContext);
      if (!(generatedIdToken instanceof Jwt jwt)) {
        throw new OAuth2AuthenticationException(
            new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "failed to generate id token", null));
      }
      OidcIdToken issuedIdToken =
          new OidcIdToken(
              jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims());
      idToken = issuedIdToken;
      authorizationBuilder.token(
          issuedIdToken,
          metadata ->
              metadata.put(
                  OAuth2Authorization.Token.CLAIMS_METADATA_NAME, issuedIdToken.getClaims()));
      additionalParameters.put(OidcParameterNames.ID_TOKEN, issuedIdToken.getTokenValue());
    }

    authorizationService.save(authorizationBuilder.build());
    return new OAuth2AccessTokenAuthenticationToken(
        registeredClient, clientPrincipal, accessToken, refreshToken, additionalParameters);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
  }

  static Set<String> resolveScopes(
      RegisteredClient registeredClient, PasswordGrantAuthenticationToken grant) {
    Set<String> allowed = new LinkedHashSet<>(registeredClient.getScopes());
    Object requested = grant.getAdditionalParameters().get(OAuth2ParameterNames.SCOPE);
    if (!(requested instanceof String scopeParam) || scopeParam.isBlank()) {
      return allowed;
    }
    Set<String> asked = new HashSet<>(Arrays.asList(scopeParam.trim().split("\\s+")));
    asked.retainAll(allowed);
    return asked.isEmpty() ? allowed : asked;
  }

  private static OAuth2ClientAuthenticationToken authenticatedClient(
      PasswordGrantAuthenticationToken authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof OAuth2ClientAuthenticationToken client && client.isAuthenticated()) {
      return client;
    }
    throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
  }
}
