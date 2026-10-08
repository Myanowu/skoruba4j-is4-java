package com.myano.skoruba4j.admin.config;

import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordConfiguration {
  @Bean
  public IdentityPasswordHasher identityPasswordHasher() {
    return new IdentityPasswordHasher();
  }
}
