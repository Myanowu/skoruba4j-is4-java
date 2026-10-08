package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.domain.password.PasswordVerificationResult;
import com.myano.skoruba4j.sts.security.PasswordResetService;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class AccountPasswordController {
  private final Optional<JdbcRepositories> jdbc;
  private final IdentityPasswordHasher hasher;

  public AccountPasswordController(Optional<JdbcRepositories> jdbc, IdentityPasswordHasher hasher) {
    this.jdbc = jdbc;
    this.hasher = hasher;
  }

  @GetMapping(
      value = {"/account/password", "/Manage/ChangePassword"},
      produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String form(@RequestParam(value = "ok", required = false) String ok) {
    return AccountPages.changePassword(null, ok == null ? null : "Password updated.");
  }

  @PostMapping({"/account/password", "/Manage/ChangePassword"})
  public Object update(
      Authentication authentication,
      @RequestParam(value = "currentPassword", required = false) String currentPassword,
      @RequestParam(value = "password", required = false) String password,
      @RequestParam(value = "confirmPassword", required = false) String confirmPassword) {
    String userId = authentication == null ? "" : authentication.getName();
    IdentityUser user = jdbc.flatMap(repos -> repos.users().findById(userId)).orElse(null);
    if (user == null) {
      return page(AccountPages.changePassword("Account was not found.", null));
    }
    PasswordVerificationResult check = hasher.verify(user.passwordHash(), currentPassword);
    if (check == PasswordVerificationResult.FAILED) {
      return page(AccountPages.changePassword("Current password is not correct.", null));
    }
    if (password == null || password.length() < PasswordResetService.MIN_PASSWORD_LENGTH) {
      return page(
          AccountPages.changePassword(
              "Password must be at least " + PasswordResetService.MIN_PASSWORD_LENGTH + " characters.",
              null));
    }
    if (confirmPassword == null || !password.equals(confirmPassword)) {
      return page(AccountPages.changePassword("Password and confirmation do not match.", null));
    }
    jdbc.get().users().setPasswordHash(user.id(), hasher.hash(password));
    return new RedirectView("/account/password?ok=1", true);
  }

  private static ResponseEntity<String> page(String html) {
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
        .body(html);
  }
}
