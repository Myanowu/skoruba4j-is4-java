package com.myano.skoruba4j.admin.tls;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.admin.config.IdserverProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

class StsOutboundHttpConfigurationTest {

  @Test
  void tokenResponseConverterIsRegistered() {
    RestTemplate rest = new StsOutboundHttpConfiguration().stsOutboundRestTemplate(new IdserverProperties());
    boolean found = false;
    for (HttpMessageConverter<?> converter : rest.getMessageConverters()) {
      if (converter instanceof OAuth2AccessTokenResponseHttpMessageConverter) {
        found = true;
        break;
      }
    }
    assertTrue(found);
  }
}
