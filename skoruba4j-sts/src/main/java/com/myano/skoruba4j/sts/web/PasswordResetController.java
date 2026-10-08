package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.sts.security.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class PasswordResetController {
  private final PasswordResetService resets;

  public PasswordResetController(PasswordResetService resets) {
    this.resets = resets;
  }

  @GetMapping(
      value = {"/forgot-password", "/Account/ForgotPassword"},
      produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String forgotForm() {
    return AccountPages.forgot(null);
  }

  @PostMapping({"/forgot-password", "/Account/ForgotPassword"})
  public RedirectView forgotSubmit(
      HttpServletRequest request, @RequestParam(value = "email", required = false) String email) {
    resets.requestByEmail(email, resetBase(request));
    return new RedirectView("/forgot-password/confirmation", true);
  }

  @GetMapping(
      value = {"/forgot-password/confirmation", "/Account/ForgotPasswordConfirmation"},
      produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String forgotDone() {
    return AccountPages.forgotConfirmation();
  }

  @GetMapping(
      value = {"/reset-password", "/Account/ResetPassword"},
      produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String resetForm(@RequestParam(value = "code", required = false) String code) {
    if (code == null || code.isBlank()) {
      return AccountPages.invalidReset();
    }
    return AccountPages.reset(code, null);
  }

  @PostMapping({"/reset-password", "/Account/ResetPassword"})
  public Object resetSubmit(
      @RequestParam(value = "email", required = false) String email,
      @RequestParam(value = "password", required = false) String password,
      @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
      @RequestParam(value = "code", required = false) String code) {
    String error = resets.complete(email, password, confirmPassword, code);
    if (error != null) {
      return ResponseEntity.ok()
          .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
          .body(AccountPages.reset(code, error));
    }
    return new RedirectView("/reset-password/confirmation", true);
  }

  @GetMapping(
      value = {"/reset-password/confirmation", "/Account/ResetPasswordConfirmation"},
      produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String resetDone() {
    return AccountPages.resetConfirmation();
  }

  static String resetBase(HttpServletRequest request) {
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    boolean defaultPort =
        ("http".equalsIgnoreCase(scheme) && port == 80)
            || ("https".equalsIgnoreCase(scheme) && port == 443);
    StringBuilder base = new StringBuilder();
    base.append(scheme).append("://").append(host);
    if (!defaultPort && port > 0) {
      base.append(':').append(port);
    }
    base.append("/reset-password");
    return base.toString();
  }
}
