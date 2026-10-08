package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.sts.security.IdentityUserDetailsService;
import com.myano.skoruba4j.sts.security.Is4LoginSuccessHandler;
import com.myano.skoruba4j.sts.security.externallogin.ExternalIdentityLinker;
import com.myano.skoruba4j.sts.security.externallogin.ExternalLoginLinkResult;
import com.myano.skoruba4j.sts.security.externallogin.ExternalLoginSession;
import com.myano.skoruba4j.sts.security.externallogin.PendingExternalLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ExternalConfirmController {
  private final ExternalIdentityLinker linker;
  private final IdentityUserDetailsService users;
  private final Is4LoginSuccessHandler resume;

  public ExternalConfirmController(
      ExternalIdentityLinker linker,
      IdentityUserDetailsService users,
      RequestCache requestCache,
      SecurityContextRepository securityContextRepository) {
    this.linker = linker;
    this.users = users;
    this.resume = new Is4LoginSuccessHandler(securityContextRepository);
    this.resume.setRequestCache(requestCache);
  }

  @GetMapping(value = "/external/confirm", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String form(HttpServletRequest request) {
    PendingExternalLogin pending = ExternalLoginSession.pending(request);
    if (pending == null) {
      return redirectLogin("external-no-pending");
    }
    CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    String param = csrf == null ? "_csrf" : csrf.getParameterName();
    String token = csrf == null ? "" : csrf.getToken();
    StringBuilder body = new StringBuilder();
    boolean phoneOrWeChat =
        "whatsapp".equalsIgnoreCase(pending.loginProvider())
            || "wechat".equalsIgnoreCase(pending.loginProvider());
    body.append("<p class=\"lead\">Finish creating a local account linked to ")
        .append(LoginPage.esc(pending.loginProvider()))
        .append(".</p>");
    body.append("<form method=\"post\" action=\"/external/confirm\">");
    body.append("<label for=\"userName\">Username</label>");
    body.append("<input id=\"userName\" name=\"userName\" type=\"text\" required value=\"")
        .append(LoginPage.esc(nullToEmpty(pending.suggestedUserName())))
        .append("\">");
    body.append("<label for=\"email\">Email</label>");
    body.append("<input id=\"email\" name=\"email\" type=\"email\"")
        .append(phoneOrWeChat ? "" : " required")
        .append(" value=\"")
        .append(LoginPage.esc(nullToEmpty(pending.email())))
        .append("\">");
    if (phoneOrWeChat) {
      body.append(
          "<p class=\"muted\">Email is optional for WhatsApp / WeChat when creating from this flow.</p>");
    }
    body.append(StsPages.hidden(param, token));
    body.append("<button class=\"primary\" type=\"submit\">Create and sign in</button>");
    body.append("</form>");
    body.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/login\">Cancel</a></p>");
    return StsPages.document(
        "Confirm account",
        "auth",
        StsPages.card("Skoruba4j STS", "Confirm account", null, body.toString()));
  }

  @PostMapping("/external/confirm")
  public void submit(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestParam(value = "userName", required = false) String userName,
      @RequestParam(value = "email", required = false) String email)
      throws IOException {
    PendingExternalLogin pending = ExternalLoginSession.pending(request);
    if (pending == null) {
      response.sendRedirect("/login?error=external-no-pending");
      return;
    }
    ExternalLoginLinkResult result = linker.completeConfirm(pending, userName, email);
    if (result instanceof ExternalLoginLinkResult.Rejected rejected) {
      response.sendRedirect(
          "/login?error="
              + URLEncoder.encode(rejected.messageKey(), StandardCharsets.UTF_8));
      return;
    }
    if (!(result instanceof ExternalLoginLinkResult.Authenticated ok)) {
      response.sendRedirect("/login?error=external");
      return;
    }
    ExternalLoginSession.clearPending(request);
    UserDetails details = users.userDetailsFor(ok.user());
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            details, details.getPassword(), details.getAuthorities());
    try {
      resume.onAuthenticationSuccess(request, response, token);
    } catch (Exception e) {
      response.sendRedirect("/login?error=external");
    }
  }

  private static String redirectLogin(String code) {
    return "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url=/login?error="
        + LoginPage.esc(code)
        + "\"></head><body></body></html>";
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
