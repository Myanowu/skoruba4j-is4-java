package com.myano.skoruba4j.adminapi.config;

import com.myano.skoruba4j.adminapi.web.ui.ApiUiHtml;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = "idserver.admin",
    name = "api-ui-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ApiUiHtmlConfiguration {
  private final IdserverProperties properties;

  public ApiUiHtmlConfiguration(IdserverProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void bindAdminRole() {
    ApiUiHtml.setRequiredAdminRole(properties.adminRole());
  }
}
