package com.myano.skoruba4j.adminapi.tls;

import com.myano.skoruba4j.adminapi.config.IdserverProperties;
import com.myano.skoruba4j.tls.OutboundTrust;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * Outbound HTTPS to STS for Admin API OIDC (token + JWKS). Active when UI is on (login mode may
 * still be local-only; bean is cheap).
 */
@Configuration
@ConditionalOnProperty(
    prefix = "idserver.admin",
    name = "api-ui-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class StsOutboundHttpConfiguration {

  @Bean
  RestTemplate stsOutboundRestTemplate(
      IdserverProperties properties,
      @Value("${server.ssl.key-store:}") String serverKeyStore,
      @Value("${server.ssl.key-store-password:}") String serverKeyStorePassword,
      @Value("${server.ssl.key-store-type:}") String serverKeyStoreType) {
    HttpClient http =
        HttpClient.newBuilder()
            .sslContext(
                OutboundTrust.sslContext(
                    properties.tlsTrustStore(),
                    properties.tlsTrustStorePassword(),
                    properties.tlsTrustStoreType(),
                    serverKeyStore,
                    serverKeyStorePassword,
                    serverKeyStoreType))
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    RestTemplate rest = new RestTemplate(new JdkClientHttpRequestFactory(http));
    List<HttpMessageConverter<?>> converters = new ArrayList<>();
    converters.add(new FormHttpMessageConverter());
    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
    converters.add(new OAuth2ErrorHttpMessageConverter());
    converters.addAll(rest.getMessageConverters());
    rest.setMessageConverters(converters);
    rest.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
    return rest;
  }
}
