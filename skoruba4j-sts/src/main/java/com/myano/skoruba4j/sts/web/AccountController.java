package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Account self-service lives on the signed-in home page ({@code /}). {@code /account} redirects
 * there; password change stays at {@code /account/password}.
 */
@Controller
public class AccountController {
  private final Optional<JdbcRepositories> jdbc;

  public AccountController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/account")
  public RedirectView account() {
    return new RedirectView("/", true);
  }

  @PostMapping("/account/providers/delete")
  public RedirectView unlink(
      Authentication authentication,
      @RequestParam("loginProvider") String loginProvider,
      @RequestParam("providerKey") String providerKey) {
    String userId = authentication == null ? "" : authentication.getName();
    if (jdbc.isEmpty() || userId.isBlank()) {
      return new RedirectView("/login", true);
    }
    int n = jdbc.get().users().deleteLogin(userId, loginProvider, providerKey);
    String ok = n > 0 ? "unlinked" : "missing";
    return new RedirectView("/?ok=" + ok, true);
  }
}
