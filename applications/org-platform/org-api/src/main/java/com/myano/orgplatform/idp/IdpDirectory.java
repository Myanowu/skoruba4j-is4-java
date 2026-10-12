package com.myano.orgplatform.idp;

import java.util.List;
import java.util.Optional;

/** Read and create STS users through the Admin API. The join key is {@code Users.Id}. */
public interface IdpDirectory {

  /** Whether Admin API calls are configured. */
  boolean enabled();

  /** Searches users by username or email. */
  List<IdpAccount> search(String text);

  /** Loads one user by {@code Users.Id}. */
  Optional<IdpAccount> findById(String id);

  /** Creates a user. Callers must reject an existing username before this. */
  IdpAccount create(String userName, String email, String password);

  /** STS user fields the org console needs. Password is never included. */
  record IdpAccount(String id, String userName, String email) {}
}
