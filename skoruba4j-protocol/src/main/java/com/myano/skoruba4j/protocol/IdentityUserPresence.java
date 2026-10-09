package com.myano.skoruba4j.protocol;

/**
 * Whether a restored login still names a row in the identity database this process is using.
 * STS supplies the lookup; the auth cookie itself only stores an id.
 */
public interface IdentityUserPresence {
  /** False when this process has no identity database to check against. */
  boolean databaseConfigured();

  /** True when {@code userId} is {@code Users.Id} in the current database. */
  boolean exists(String userId);
}
