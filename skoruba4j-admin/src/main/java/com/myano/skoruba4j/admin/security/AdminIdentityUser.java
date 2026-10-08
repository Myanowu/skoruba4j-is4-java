package com.myano.skoruba4j.admin.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Local form-login principal. {@link #getUsername()} is the Identity user id (sub). */
public final class AdminIdentityUser implements UserDetails, Serializable {
  @Serial private static final long serialVersionUID = 1L;

  private final String id;
  private final String userName;
  private final String email;
  private final String passwordHash;
  private final boolean locked;
  private final List<GrantedAuthority> authorities;

  public AdminIdentityUser(
      String id,
      String userName,
      String email,
      String passwordHash,
      boolean locked,
      List<GrantedAuthority> authorities) {
    this.id = id;
    this.userName = userName;
    this.email = email;
    this.passwordHash = passwordHash;
    this.locked = locked;
    this.authorities = authorities == null ? List.of() : List.copyOf(authorities);
  }

  public String id() {
    return id;
  }

  public String userName() {
    return userName;
  }

  public String email() {
    return email;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return id;
  }

  @Override
  public boolean isAccountNonLocked() {
    return !locked;
  }
}
