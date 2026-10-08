package com.myano.skoruba4j.sts.config;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.sts.security.PasswordResetService;
import com.myano.skoruba4j.sts.security.PasswordResetTokens;
import com.myano.skoruba4j.sts.security.ResetMailSender;
import com.myano.skoruba4j.sts.security.SmtpResetMailSender;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordConfiguration {

  @Bean
  public IdentityPasswordHasher identityPasswordHasher() {
    return new IdentityPasswordHasher();
  }

  @Bean
  public PasswordResetTokens passwordResetTokens(IdserverProperties properties) {
    IdserverProperties.Smtp smtp = properties.getSmtp();
    String resetKey = smtp == null ? "" : smtp.getResetKey();
    return new PasswordResetTokens(
        PasswordResetTokens.keyFrom(resetKey, properties.getIssuerUri()), Clock.systemUTC());
  }

  @Bean
  public ResetMailSender resetMailSender(IdserverProperties properties) {
    return new SmtpResetMailSender(properties.getSmtp());
  }

  @Bean
  public PasswordResetService passwordResetService(
      Optional<JdbcRepositories> jdbc,
      PasswordResetTokens tokens,
      ResetMailSender mail,
      IdentityPasswordHasher hasher) {
    return new PasswordResetService(jdbc, tokens, mail, hasher);
  }
}
