package com.myano.skoruba4j.admin.security;

import com.myano.skoruba4j.admin.config.IdserverProperties;
import com.myano.skoruba4j.domain.configstore.AdminUiClientIds;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/** MobileWeb-style ROPC against STS {@code /connect/token}. */
@Component
public class StsPasswordLoginClient {
  private static final Logger log = LoggerFactory.getLogger(StsPasswordLoginClient.class);

  private final IdserverProperties properties;
  private final Optional<JdbcRepositories> jdbc;
  private final RestTemplate stsOutboundRestTemplate;

  public StsPasswordLoginClient(
      IdserverProperties properties,
      Optional<JdbcRepositories> jdbc,
      RestTemplate stsOutboundRestTemplate) {
    this.properties = properties;
    this.jdbc = jdbc;
    this.stsOutboundRestTemplate = stsOutboundRestTemplate;
  }

  /**
   * Exchanges resource-owner credentials for tokens at the configured issuer. Failures become
   * {@link BadCredentialsException}; OAuth error bodies are logged at warn (no password).
   */
  public OAuth2AccessTokenResponse requestToken(String username, String password) {
    String clientId = AdminUiClientIds.resolve(properties.adminClientId(), jdbc);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add(OAuth2ParameterNames.GRANT_TYPE, "password");
    form.add(OAuth2ParameterNames.USERNAME, username);
    form.add(OAuth2ParameterNames.PASSWORD, password);
    form.add(OAuth2ParameterNames.CLIENT_ID, clientId);
    String secret = properties.adminClientSecret();
    if (secret != null && !secret.isBlank()) {
      form.add(OAuth2ParameterNames.CLIENT_SECRET, secret);
    }
    String scope = String.join(" ", properties.oidcScopes());
    if (!scope.isBlank()) {
      form.add(OAuth2ParameterNames.SCOPE, scope);
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    String tokenUrl = properties.issuerUri().replaceAll("/$", "") + "/connect/token";
    try {
      OAuth2AccessTokenResponse response =
          stsOutboundRestTemplate.postForObject(
              tokenUrl, new HttpEntity<>(form, headers), OAuth2AccessTokenResponse.class);
      if (response == null || response.getAccessToken() == null) {
        throw new BadCredentialsException("Invalid username or password");
      }
      return response;
    } catch (OAuth2AuthorizationException ex) {
      // RestTemplate + OAuth2ErrorResponseErrorHandler throws this (not RestClientException).
      log.warn(
          "STS password grant failed client={} error={} desc={}",
          clientId,
          ex.getError() == null ? "?" : ex.getError().getErrorCode(),
          ex.getError() == null ? "" : ex.getError().getDescription());
      throw new BadCredentialsException("Invalid username or password", ex);
    } catch (RestClientResponseException ex) {
      log.warn(
          "STS password grant failed client={} status={} body={}",
          clientId,
          ex.getStatusCode().value(),
          ex.getResponseBodyAsString());
      throw new BadCredentialsException("Invalid username or password", ex);
    } catch (RestClientException ex) {
      log.warn(
          "STS password grant call failed client={} url={}: {}", clientId, tokenUrl, ex.toString());
      throw new BadCredentialsException("Invalid username or password", ex);
    }
  }
}
