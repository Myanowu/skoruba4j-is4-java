package com.myano.skoruba4j.domain.password;

/** Result of checking an ASP.NET Identity stored hash. */
public enum PasswordVerificationResult {
  FAILED,
  SUCCESS,
  /** Correct password, but stored as V2 or a weaker V3 iteration count — rewrite as V3. */
  SUCCESS_REHASH_NEEDED
}
