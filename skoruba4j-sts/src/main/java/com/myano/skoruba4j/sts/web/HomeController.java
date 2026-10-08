package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {
  private final Optional<JdbcRepositories> jdbc;

  public HomeController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String home(Authentication authentication) {
    String userId = authentication == null ? "" : authentication.getName();
    IdentityUser user =
        jdbc.flatMap(repos -> repos.users().findById(userId)).orElse(null);
    if (user == null) {
      return SignedInPage.render(userId, userId, null);
    }
    return SignedInPage.render(user.id(), user.userName(), user.email());
  }
}
