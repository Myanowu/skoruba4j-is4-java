package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.UserLogin;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {
  private final Optional<JdbcRepositories> jdbc;

  public HomeController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String home(
      Authentication authentication, @RequestParam(value = "ok", required = false) String ok) {
    String userId = authentication == null ? "" : authentication.getName();
    IdentityUser user = jdbc.flatMap(repos -> repos.users().findById(userId)).orElse(null);
    List<UserLogin> logins =
        jdbc.map(repos -> repos.users().listLogins(userId)).orElse(List.of());
    if (user == null) {
      return SignedInPage.render(userId, userId, null, false, logins, ok);
    }
    return SignedInPage.render(
        user.id(), user.userName(), user.email(), user.twoFactorEnabled(), logins, ok);
  }
}
