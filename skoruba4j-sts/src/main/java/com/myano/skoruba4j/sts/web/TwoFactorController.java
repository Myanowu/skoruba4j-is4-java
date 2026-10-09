package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.TotpCodes;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import com.myano.skoruba4j.sts.security.IdentityUserDetailsService;
import com.myano.skoruba4j.sts.security.Is4LoginSuccessHandler;
import com.myano.skoruba4j.sts.security.TwoFactorLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TwoFactorController {
  private final Optional<JdbcRepositories> jdbc;
  private final IdentityUserDetailsService users;
  private final SecurityContextRepository securityContextRepository;
  private final RegisteredClientRepository clients;

  public TwoFactorController(
      Optional<JdbcRepositories> jdbc,
      IdentityUserDetailsService users,
      SecurityContextRepository securityContextRepository,
      RegisteredClientRepository clients) {
    this.jdbc = jdbc;
    this.users = users;
    this.securityContextRepository = securityContextRepository;
    this.clients = clients;
  }

  @GetMapping(value = "/login/2fa", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String form(
      HttpServletRequest request,
      @RequestParam(value = "ReturnUrl", required = false) String returnUrl,
      @RequestParam(value = "error", required = false) String error) {
    String userId = TwoFactorLogin.userId(request.getSession(false));
    if (userId == null || userId.isBlank()) {
      return redirectLogin(returnUrl);
    }
    String safeReturn =
        Is4ReturnUrls.isSafe(returnUrl)
            ? returnUrl
            : TwoFactorLogin.returnUrl(request.getSession(false));
    String action = "/login/2fa";
    if (Is4ReturnUrls.isSafe(safeReturn)) {
      action = action + "?ReturnUrl=" + URLEncoder.encode(safeReturn, StandardCharsets.UTF_8);
    }
    StringBuilder body = new StringBuilder();
    if (error != null) {
      body.append(StsPages.notice("err", "Invalid or expired code. Try again."));
    }
    body.append("<p class=\"muted\">Enter the 6-digit code from your authenticator app.</p>");
    body.append("<form method=\"post\" action=\"")
        .append(LoginPage.esc(action))
        .append("\" autocomplete=\"one-time-code\">");
    body.append("<label for=\"code\">Code</label>");
    body.append(
        "<input id=\"code\" name=\"code\" inputmode=\"numeric\" pattern=\"[0-9]{6}\" maxlength=\"6\" required autofocus>");
    body.append("<button class=\"primary\" type=\"submit\">Verify</button>");
    body.append("</form>");
    body.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/login");
    if (Is4ReturnUrls.isSafe(safeReturn)) {
      body.append("?ReturnUrl=")
          .append(URLEncoder.encode(safeReturn, StandardCharsets.UTF_8));
    }
    body.append("\">Back to sign in</a></p>");
    return StsPages.document(
        "Two-factor authentication",
        "auth",
        StsPages.card(
            "Skoruba4j STS",
            "Authenticator code",
            "Second step after password.",
            body.toString()));
  }

  @PostMapping("/login/2fa")
  public void verify(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestParam("code") String code,
      @RequestParam(value = "ReturnUrl", required = false) String returnUrl)
      throws Exception {
    String userId = TwoFactorLogin.userId(request.getSession(false));
    if (userId == null || jdbc.isEmpty()) {
      response.sendRedirect("/login");
      return;
    }
    Optional<String> key = jdbc.get().users().findAuthenticatorKey(userId);
    Optional<IdentityUser> user = jdbc.get().users().findById(userId);
    String safeReturn =
        Is4ReturnUrls.isSafe(returnUrl)
            ? returnUrl
            : TwoFactorLogin.returnUrl(request.getSession(false));
    if (key.isEmpty() || user.isEmpty() || !TotpCodes.verify(key.get(), code)) {
      String location = "/login/2fa?error=1";
      if (Is4ReturnUrls.isSafe(safeReturn)) {
        location =
            location
                + "&ReturnUrl="
                + URLEncoder.encode(safeReturn, StandardCharsets.UTF_8);
      }
      response.sendRedirect(location);
      return;
    }
    TwoFactorLogin.clear(request.getSession(false));
    UserDetails details = users.userDetailsFor(user.get());
    UsernamePasswordAuthenticationToken auth =
        UsernamePasswordAuthenticationToken.authenticated(
            details, details.getPassword(), details.getAuthorities());
    Is4LoginSuccessHandler success =
        new Is4LoginSuccessHandler(securityContextRepository, clients, jdbc);
    success.setRequestCache(new HttpSessionRequestCache());
    HttpServletRequest withReturn =
        Is4ReturnUrls.isSafe(safeReturn)
            ? new ReturnUrlRequest(request, safeReturn)
            : request;
    success.onAuthenticationSuccess(withReturn, response, auth);
  }

  private static String redirectLogin(String returnUrl) {
    String target =
        Is4ReturnUrls.isSafe(returnUrl)
            ? "/login?ReturnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8)
            : "/login";
    return "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url="
        + target
        + "\"></head><body></body></html>";
  }

  /** Exposes {@code ReturnUrl} to {@link Is4LoginSuccessHandler#determineTargetUrl}. */
  static final class ReturnUrlRequest extends HttpServletRequestWrapper {
    private final String returnUrl;

    ReturnUrlRequest(HttpServletRequest request, String returnUrl) {
      super(request);
      this.returnUrl = returnUrl;
    }

    @Override
    public String getParameter(String name) {
      if ("ReturnUrl".equals(name)) {
        return returnUrl;
      }
      return super.getParameter(name);
    }
  }
}
