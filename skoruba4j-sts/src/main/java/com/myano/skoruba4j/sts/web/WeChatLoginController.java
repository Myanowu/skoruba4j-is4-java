package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.externallogin.ExternalLoginClientSettings;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginLinkMode;
import com.myano.skoruba4j.sts.config.IdserverProperties;
import com.myano.skoruba4j.sts.security.IdentityUserDetailsService;
import com.myano.skoruba4j.sts.security.Is4LoginSuccessHandler;
import com.myano.skoruba4j.sts.security.externallogin.AuthorizeClientIds;
import com.myano.skoruba4j.sts.security.externallogin.ExternalIdentityLinker;
import com.myano.skoruba4j.sts.security.externallogin.ExternalLoginLinkResult;
import com.myano.skoruba4j.sts.security.externallogin.ExternalLoginSession;
import com.myano.skoruba4j.sts.security.externallogin.WeChatOpenApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * WeChat Open Platform website QR login ({@code qrconnect}). Not Spring oauth2Login — WeChat token
 * / userinfo shapes are non-OIDC.
 */
@Controller
public class WeChatLoginController {
  public static final String STATE_ATTR = "skoruba4j.external.wechat.state";

  private final IdserverProperties props;
  private final ExternalIdentityLinker linker;
  private final WeChatOpenApi weChatApi;
  private final IdentityUserDetailsService users;
  private final Is4LoginSuccessHandler resume;
  private final SecureRandom random = new SecureRandom();

  public WeChatLoginController(
      IdserverProperties props,
      ExternalIdentityLinker linker,
      WeChatOpenApi weChatApi,
      IdentityUserDetailsService users,
      RequestCache requestCache,
      SecurityContextRepository securityContextRepository) {
    this.props = props;
    this.linker = linker;
    this.weChatApi = weChatApi;
    this.users = users;
    this.resume = new Is4LoginSuccessHandler(securityContextRepository);
    this.resume.setRequestCache(requestCache);
  }

  @GetMapping(value = "/external/wechat", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String start(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Optional<String> clientId = AuthorizeClientIds.resolve(request);
    clientId.ifPresent(id -> ExternalLoginSession.setClientId(request, id));
    String resolved = clientId.orElse(ExternalLoginSession.clientId(request));
    if (!allowWeChat(resolved)) {
      response.sendRedirect("/login?error=external-disabled");
      return null;
    }
    if (!props.wechatLoginConfigured()) {
      return setupNeeded();
    }
    String state = HexFormat.of().formatHex(random.generateSeed(16));
    HttpSession session = request.getSession(true);
    session.setAttribute(STATE_ATTR, state);
    String callback = absoluteCallback(request);
    String appId = props.getExternalLogin().getWechat().getAppId().trim();
    response.sendRedirect(WeChatOpenApi.authorizeUrl(appId, callback, state));
    return null;
  }

  @GetMapping("/external/wechat/callback")
  public void callback(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "errcode", required = false) String errcode)
      throws IOException {
    if (!props.wechatLoginConfigured()) {
      response.sendRedirect("/login?error=external-disabled");
      return;
    }
    if (errcode != null && !errcode.isBlank()) {
      response.sendRedirect("/login?error=external");
      return;
    }
    HttpSession session = request.getSession(false);
    Object expected = session == null ? null : session.getAttribute(STATE_ATTR);
    if (session != null) {
      session.removeAttribute(STATE_ATTR);
    }
    if (expected == null
        || state == null
        || !String.valueOf(expected).equals(state)
        || code == null
        || code.isBlank()) {
      response.sendRedirect("/login?error=external");
      return;
    }
    String clientId = ExternalLoginSession.clientId(request);
    if (!allowWeChat(clientId)) {
      response.sendRedirect("/login?error=external-disabled");
      return;
    }
    var wechat = props.getExternalLogin().getWechat();
    Optional<WeChatOpenApi.TokenResult> token =
        weChatApi.exchangeCode(wechat.getAppId(), wechat.getAppSecret(), code);
    if (token.isEmpty()) {
      response.sendRedirect("/login?error=external");
      return;
    }
    WeChatOpenApi.TokenResult t = token.get();
    String nickname =
        weChatApi
            .userInfo(t.accessToken(), t.openId())
            .map(WeChatOpenApi.Profile::nickname)
            .orElse("");
    ExternalLoginLinkMode mode =
        clientId == null || clientId.isBlank()
            ? ExternalLoginLinkMode.LINK_EXISTING
            : linker.settingsForClient(clientId).wechatLinkMode();
    ExternalLoginLinkResult result =
        linker.linkWeChat(t.openId(), t.unionId(), nickname, mode);
    if (result instanceof ExternalLoginLinkResult.NeedConfirm need) {
      ExternalLoginSession.setPending(request, need.pending());
      response.sendRedirect("/external/confirm");
      return;
    }
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
    UserDetails details = users.userDetailsFor(ok.user());
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            details, details.getPassword(), details.getAuthorities());
    try {
      resume.onAuthenticationSuccess(request, response, auth);
    } catch (Exception e) {
      response.sendRedirect("/login?error=external");
    }
  }

  private boolean allowWeChat(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      return props.getExternalLogin().getWechat().isAllowDirectLogin();
    }
    ExternalLoginClientSettings settings = linker.settingsForClient(clientId);
    return settings.wechatEnabled();
  }

  private static String setupNeeded() {
    StringBuilder body = new StringBuilder();
    body.append(
        "<p class=\"lead\">WeChat QR needs Open Platform app id + secret before the scan page can open.</p>");
    body.append("<ol class=\"muted\" style=\"padding-left:1.2rem\">");
    body.append(
        "<li>Control → Settings → <strong>External IdP</strong> → WeChat → App ID / App secret</li>");
    body.append(
        "<li>Register callback <code>{issuer}/external/wechat/callback</code> in WeChat console</li>");
    body.append("<li>Save, restart STS</li>");
    body.append("<li>Admin → Clients → enable <strong>Allow WeChat QR login</strong></li>");
    body.append("</ol>");
    body.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/login\">Back to sign in</a></p>");
    return StsPages.document(
        "WeChat setup",
        "auth",
        StsPages.card("Skoruba4j STS", "Configure WeChat", null, body.toString()));
  }

  private String absoluteCallback(HttpServletRequest request) {
    String issuer = props.getIssuerUri();
    if (issuer != null && !issuer.isBlank()) {
      String root = issuer.trim();
      if (root.endsWith("/")) {
        root = root.substring(0, root.length() - 1);
      }
      return root + "/external/wechat/callback";
    }
    StringBuffer url = request.getRequestURL();
    String uri = request.getRequestURI();
    String origin = url.substring(0, url.length() - uri.length());
    String context = request.getContextPath() == null ? "" : request.getContextPath();
    return origin + context + "/external/wechat/callback";
  }
}
