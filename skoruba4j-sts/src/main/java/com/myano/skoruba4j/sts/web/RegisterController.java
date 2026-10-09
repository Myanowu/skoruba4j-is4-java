package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.sts.config.IdserverProperties;
import com.myano.skoruba4j.sts.security.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Optional self-registration for demo / public installs ({@code idserver.login.allow-register}).
 * Default off. Captcha is a simple session math challenge (no third-party reCAPTCHA required).
 */
@Controller
public class RegisterController {
  private static final String CAPTCHA_SESSION = "skoruba4j.register.captcha";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final IdserverProperties props;
  private final Optional<JdbcRepositories> jdbc;
  private final IdentityPasswordHasher hasher;

  public RegisterController(
      IdserverProperties props, Optional<JdbcRepositories> jdbc, IdentityPasswordHasher hasher) {
    this.props = props;
    this.jdbc = jdbc;
    this.hasher = hasher;
  }

  @GetMapping(value = "/register", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public Object form(
      HttpServletRequest request, @RequestParam(value = "error", required = false) String error) {
    if (!props.getLogin().isAllowRegister()) {
      return disabledPage();
    }
    String captchaPrompt = props.getLogin().isRegisterCaptcha() ? issueCaptcha(request) : null;
    return AccountPages.register(
        props.getBrand().getProductName(),
        errorMessage(error),
        captchaPrompt);
  }

  @PostMapping("/register")
  public Object create(
      HttpServletRequest request,
      @RequestParam(value = "userName", required = false) String userName,
      @RequestParam(value = "email", required = false) String email,
      @RequestParam(value = "password", required = false) String password,
      @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
      @RequestParam(value = "captcha", required = false) String captcha) {
    if (!props.getLogin().isAllowRegister()) {
      return disabledPage();
    }
    if (jdbc.isEmpty()) {
      return redirectError("db");
    }
    if (props.getLogin().isRegisterCaptcha() && !checkCaptcha(request, captcha)) {
      return redirectError("captcha");
    }
    String user = userName == null ? "" : userName.trim();
    String mail = email == null ? "" : email.trim();
    if (user.isBlank() || user.length() < 3) {
      return redirectError("username");
    }
    if (mail.isBlank() || !mail.contains("@")) {
      return redirectError("email");
    }
    if (password == null || password.length() < PasswordResetService.MIN_PASSWORD_LENGTH) {
      return redirectError("password");
    }
    if (confirmPassword == null || !password.equals(confirmPassword)) {
      return redirectError("confirm");
    }
    String normalizedUser = user.toUpperCase(Locale.ROOT);
    String normalizedEmail = mail.toUpperCase(Locale.ROOT);
    if (jdbc.get().users().findByNormalizedUserName(normalizedUser).isPresent()) {
      return redirectError("taken");
    }
    if (jdbc.get().users().findByNormalizedEmail(normalizedEmail).isPresent()) {
      return redirectError("email-taken");
    }
    jdbc.get().users().insert(user, mail, false, hasher.hash(password));
    clearCaptcha(request);
    return new RedirectView("/login?registered=1", true);
  }

  private Object disabledPage() {
    String html =
        AccountPages.registerDisabled(props.getBrand().getProductName());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
        .body(html);
  }

  private static RedirectView redirectError(String code) {
    return new RedirectView("/register?error=" + code, true);
  }

  private static String errorMessage(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    return switch (code) {
      case "captcha" -> "Captcha answer is not correct.";
      case "username" -> "Username must be at least 3 characters.";
      case "email" -> "A valid email is required.";
      case "password" ->
          "Password must be at least " + PasswordResetService.MIN_PASSWORD_LENGTH + " characters.";
      case "confirm" -> "Password and confirmation do not match.";
      case "taken" -> "That username is already taken.";
      case "email-taken" -> "That email is already registered.";
      case "db" -> "Database is not configured.";
      default -> "Registration could not be completed.";
    };
  }

  private static String issueCaptcha(HttpServletRequest request) {
    int a = 1 + RANDOM.nextInt(8);
    int b = 1 + RANDOM.nextInt(8);
    HttpSession session = request.getSession(true);
    session.setAttribute(CAPTCHA_SESSION, Integer.valueOf(a + b));
    return a + " + " + b + " = ?";
  }

  private static boolean checkCaptcha(HttpServletRequest request, String answer) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return false;
    }
    Object expected = session.getAttribute(CAPTCHA_SESSION);
    session.removeAttribute(CAPTCHA_SESSION);
    if (!(expected instanceof Integer sum) || answer == null || answer.isBlank()) {
      return false;
    }
    try {
      return sum.intValue() == Integer.parseInt(answer.trim());
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static void clearCaptcha(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(CAPTCHA_SESSION);
    }
  }
}
