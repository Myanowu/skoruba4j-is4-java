package com.myano.skoruba4j.adminapi.security;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public final class UiIdentityUserDetailsService implements UserDetailsService {
  private final Optional<JdbcRepositories> jdbc;

  public UiIdentityUserDetailsService(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    JdbcRepositories repos =
        jdbc.orElseThrow(() -> new UsernameNotFoundException("database not configured"));
    String normalized = username == null ? "" : username.trim().toUpperCase(Locale.ROOT);
    IdentityUser user =
        repos
            .users()
            .findByNormalizedUserName(normalized)
            .or(() -> repos.users().findByNormalizedEmail(normalized))
            .or(() -> username == null ? Optional.empty() : repos.users().findById(username.trim()))
            .orElseThrow(() -> new UsernameNotFoundException(username));
    if (user.passwordHash() == null || user.passwordHash().isBlank()) {
      throw new UsernameNotFoundException(username);
    }
    boolean locked =
        user.lockoutEnabled()
            && user.lockoutEnd() != null
            && user.lockoutEnd().isAfter(Instant.now());
    List<SimpleGrantedAuthority> authorities =
        repos.users().listRoleNames(user.id()).stream()
            .filter(n -> n != null && !n.isBlank())
            .map(SimpleGrantedAuthority::new)
            .toList();
    return new UiIdentityUser(
        user.id(),
        user.userName(),
        user.email(),
        user.passwordHash(),
        locked,
        List.copyOf(authorities));
  }
}
