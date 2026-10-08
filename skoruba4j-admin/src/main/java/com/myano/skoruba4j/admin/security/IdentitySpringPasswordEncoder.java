package com.myano.skoruba4j.admin.security;

import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.domain.password.PasswordVerificationResult;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class IdentitySpringPasswordEncoder implements PasswordEncoder {
  private final IdentityPasswordHasher hasher;

  public IdentitySpringPasswordEncoder(IdentityPasswordHasher hasher) {
    this.hasher = hasher;
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return hasher.hash(rawPassword.toString());
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    PasswordVerificationResult result = hasher.verify(encodedPassword, rawPassword.toString());
    return result == PasswordVerificationResult.SUCCESS
        || result == PasswordVerificationResult.SUCCESS_REHASH_NEEDED;
  }
}
