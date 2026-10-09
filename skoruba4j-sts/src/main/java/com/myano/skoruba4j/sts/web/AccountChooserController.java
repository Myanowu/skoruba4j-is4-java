package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.protocol.AccountChooser;
import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import com.myano.skoruba4j.sts.config.IdserverProperties;
import com.myano.skoruba4j.sts.security.externallogin.AuthorizeClientIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AccountChooserController {
  private final Optional<JdbcRepositories> jdbc;
  private final IdserverProperties props;

  public AccountChooserController(Optional<JdbcRepositories> jdbc, IdserverProperties props) {
    this.jdbc = jdbc;
    this.props = props;
  }

  @GetMapping(value = AccountChooser.CHOOSE_PATH, produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String choose(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication,
      @RequestParam(value = "ReturnUrl", required = false) String returnUrl)
      throws IOException {
    if (!props.accountChooserEnabled()) {
      if (signedIn(authentication) && Is4ReturnUrls.isSafe(returnUrl)) {
        AccountChooser.markApproved(request.getSession(true), returnUrl);
        response.sendRedirect(returnUrl);
      } else if (Is4ReturnUrls.isSafe(returnUrl)) {
        response.sendRedirect(
            "/login?ReturnUrl="
                + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8));
      } else {
        response.sendRedirect(signedIn(authentication) ? "/" : "/login");
      }
      return null;
    }
    if (!signedIn(authentication)) {
      response.sendRedirect(
          Is4ReturnUrls.isSafe(returnUrl)
              ? "/login?ReturnUrl="
                  + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8)
              : "/login");
      return null;
    }
    if (Is4ReturnUrls.isSafe(returnUrl)
        && AccountChooser.consumeFreshLogin(request.getSession(false))) {
      AccountChooser.markApproved(request.getSession(true), returnUrl);
      response.sendRedirect(returnUrl);
      return null;
    }
    AuthorizeClientIds.rememberFromReturnUrl(request, returnUrl);
    IdentityUser user = loadUser(authentication.getName());
    String userName = user == null ? authentication.getName() : user.userName();
    String email = user == null ? null : user.email();
    String clientHint =
        AuthorizeClientIds.resolve(request).orElse(null);
    return AccountChooserPage.render(userName, email == null ? userName : email, returnUrl, clientHint);
  }

  @GetMapping(AccountChooser.CHOOSE_PATH + "/continue")
  public void continueAs(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication,
      @RequestParam(value = "ReturnUrl", required = false) String returnUrl)
      throws IOException {
    if (!signedIn(authentication)) {
      response.sendRedirect("/login");
      return;
    }
    if (Is4ReturnUrls.isSafe(returnUrl)) {
      AccountChooser.markApproved(request.getSession(true), returnUrl);
      response.sendRedirect(returnUrl);
      return;
    }
    response.sendRedirect("/");
  }

  @GetMapping(AccountChooser.CHOOSE_PATH + "/other")
  public void useAnother(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestParam(value = "ReturnUrl", required = false) String returnUrl)
      throws IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    new SecurityContextLogoutHandler().logout(request, response, auth);
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    if (Is4ReturnUrls.isSafe(returnUrl)) {
      response.sendRedirect(
          "/login?ReturnUrl="
              + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8));
      return;
    }
    response.sendRedirect("/login");
  }

  private IdentityUser loadUser(String userId) {
    if (userId == null || userId.isBlank() || jdbc.isEmpty()) {
      return null;
    }
    return jdbc.get().users().findById(userId).orElse(null);
  }

  private static boolean signedIn(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }
}
