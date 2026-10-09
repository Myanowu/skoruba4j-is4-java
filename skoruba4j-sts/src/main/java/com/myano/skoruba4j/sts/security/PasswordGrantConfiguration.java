package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.protocol.PasswordGrantAuthenticationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Wires resource-owner password grant to a dedicated Identity {@link AuthenticationManager} built
 * the same way as STS form login (ASP.NET PBKDF2 + optional debug password), so Admin ROPC cannot
 * diverge from /login.
 */
@Configuration
public class PasswordGrantConfiguration {

  /**
   * Isolated ProviderManager — never Boot's UserDetailsService + client-secret PasswordEncoder
   * bean.
   */
  @Bean
  public PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider(
      UserDetailsService userDetailsService,
      IdentitySpringPasswordEncoder identityLoginPasswordEncoder,
      OAuth2AuthorizationService authorizationService,
      OAuth2TokenGenerator<?> tokenGenerator) {
    DaoAuthenticationProvider dao = new DaoAuthenticationProvider(userDetailsService);
    dao.setPasswordEncoder(identityLoginPasswordEncoder);
    AuthenticationManager identityUsers = new ProviderManager(List.of(dao));
    return new PasswordGrantAuthenticationProvider(
        identityUsers, authorizationService, tokenGenerator);
  }
}
