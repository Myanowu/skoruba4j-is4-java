package com.myano.skoruba4j.admin.security;

import com.myano.skoruba4j.admin.config.IdserverProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Loads OIDC UserInfo from STS; Admin must not query Identity tables for this dialog. */
@Component
public class StsUserInfoClient {
  private static final Logger log = LoggerFactory.getLogger(StsUserInfoClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP =
      new ParameterizedTypeReference<>() {};

  private final IdserverProperties properties;
  private final RestTemplate stsOutboundRestTemplate;

  public StsUserInfoClient(IdserverProperties properties, RestTemplate stsOutboundRestTemplate) {
    this.properties = properties;
    this.stsOutboundRestTemplate = stsOutboundRestTemplate;
  }

  /** GET {@code /connect/userinfo} with the password-grant access token. */
  public Map<String, Object> fetch(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return Map.of();
    }
    String url = properties.issuerUri().replaceAll("/$", "") + "/connect/userinfo";
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    try {
      ResponseEntity<Map<String, Object>> response =
          stsOutboundRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), MAP);
      Map<String, Object> body = response.getBody();
      if (body == null || body.isEmpty()) {
        return Map.of();
      }
      return new LinkedHashMap<>(body);
    } catch (RestClientException ex) {
      log.warn("STS userinfo failed url={}: {}", url, ex.toString());
      return Map.of();
    }
  }
}
