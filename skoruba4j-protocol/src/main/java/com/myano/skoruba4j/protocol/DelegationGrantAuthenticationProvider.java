package com.myano.skoruba4j.protocol;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
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
 * Validates {@code token}, takes {@code sub}, and issues a new access token for that subject
 * (C# {@code DelegationGrantValidator}).
 */
public final class DelegationGrantAuthenticationProvider implements AuthenticationProvider {
  private final OAuth2AuthorizationService authorizationService;
  private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
  private final JwtDecoder jwtDecoder;

  public DelegationGrantAuthenticationProvider(
      OAuth2AuthorizationService authorizationService,
      OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
      JwtDecoder jwtDecoder) {
    this.authorizationService = authorizationService;
    this.tokenGenerator = tokenGenerator;
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    DelegationGrantAuthenticationToken delegation =
        (DelegationGrantAuthenticationToken) authentication;
    OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(delegation);
    RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
    if (registeredClient == null
        || !registeredClient.getAuthorizationGrantTypes().contains(DelegationGrantAuthenticationToken.DELEGATION)) {
      throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
    }

    Jwt jwt;
    try {
      jwt = jwtDecoder.decode(delegation.getSubjectToken());
    } catch (JwtException ex) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "token is invalid", null));
    }
    String subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "token has no sub", null));
    }

    Set<String> authorizedScopes = resolveScopes(registeredClient, delegation);

    UsernamePasswordAuthenticationToken principal =
        UsernamePasswordAuthenticationToken.authenticated(
            subject, "N/A", AuthorityUtils.createAuthorityList("User"));

    DefaultOAuth2TokenContext.Builder tokenContextBuilder =
        DefaultOAuth2TokenContext.builder()
            .registeredClient(registeredClient)
            .principal(principal)
            .authorizationServerContext(AuthorizationServerContextHolder.getContext())
            .authorizedScopes(authorizedScopes)
            .authorizationGrantType(DelegationGrantAuthenticationToken.DELEGATION)
            .authorizationGrant(delegation);

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

    OAuth2RefreshToken refreshToken = null;
    if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
      OAuth2TokenContext refreshContext =
          tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
      OAuth2Token generatedRefresh = tokenGenerator.generate(refreshContext);
      if (generatedRefresh instanceof OAuth2RefreshToken generated) {
        refreshToken = generated;
      }
    }

    OAuth2Authorization.Builder authorizationBuilder =
        OAuth2Authorization.withRegisteredClient(registeredClient)
            .principalName(subject)
            .authorizationGrantType(DelegationGrantAuthenticationToken.DELEGATION)
            .authorizedScopes(authorizedScopes);
    if (generatedAccessToken instanceof ClaimAccessor claimAccessor) {
      authorizationBuilder.token(
          accessToken,
          metadata ->
              metadata.put(
                  OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims()));
    } else {
      authorizationBuilder.accessToken(accessToken);
    }
    if (refreshToken != null) {
      authorizationBuilder.refreshToken(refreshToken);
    }
    authorizationService.save(authorizationBuilder.build());
    return new OAuth2AccessTokenAuthenticationToken(
        registeredClient, clientPrincipal, accessToken, refreshToken);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return DelegationGrantAuthenticationToken.class.isAssignableFrom(authentication);
  }

  static Set<String> resolveScopes(
      RegisteredClient registeredClient, DelegationGrantAuthenticationToken delegation) {
    Set<String> allowed = new LinkedHashSet<>(registeredClient.getScopes());
    Object requested = delegation.getAdditionalParameters().get(OAuth2ParameterNames.SCOPE);
    if (!(requested instanceof String scopeParam) || scopeParam.isBlank()) {
      return allowed;
    }
    Set<String> asked = new HashSet<>(Arrays.asList(scopeParam.trim().split("\\s+")));
    asked.retainAll(allowed);
    return asked.isEmpty() ? allowed : asked;
  }

  private static OAuth2ClientAuthenticationToken authenticatedClient(
      DelegationGrantAuthenticationToken authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof OAuth2ClientAuthenticationToken client && client.isAuthenticated()) {
      return client;
    }
    throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
  }
}
