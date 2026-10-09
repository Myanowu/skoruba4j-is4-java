package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.protocol.AccountChooser;
import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import com.myano.skoruba4j.sts.config.IdserverProperties;
import com.myano.skoruba4j.sts.security.externallogin.AuthorizeClientIds;
import com.myano.skoruba4j.sts.security.externallogin.ExternalIdentityLinker;
import com.myano.skoruba4j.sts.security.externallogin.ExternalLoginSession;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppLoginMessages;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppLoginSession;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppLoginSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {
  private final IdserverProperties props;
  private final ExternalIdentityLinker linker;
  private final WhatsAppLoginSessionStore whatsappSessions;

  public LoginController(
      IdserverProperties props,
      ExternalIdentityLinker linker,
      WhatsAppLoginSessionStore whatsappSessions) {
    this.props = props;
    this.linker = linker;
    this.whatsappSessions = whatsappSessions;
  }

  @GetMapping(value = "/login", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String login(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication,
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "logout", required = false) String logout,
      @RequestParam(value = "registered", required = false) String registered,
      @RequestParam(value = "ReturnUrl", required = false) String returnUrl,
      @RequestParam(value = "idp", required = false) String idpParam)
      throws IOException {
    if (error == null
        && logout == null
        && authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      if (Is4ReturnUrls.isSafe(returnUrl)
          && AccountChooser.isFreshLogin(request.getSession(false))) {
        response.sendRedirect(returnUrl);
        return null;
      }
      if (props.accountChooserEnabled() && Is4ReturnUrls.isSafe(returnUrl)) {
        response.sendRedirect(AccountChooser.chooseRedirect(returnUrl));
        return null;
      }
      if (Is4ReturnUrls.isSafe(returnUrl)) {
        response.sendRedirect(returnUrl);
      } else {
        response.sendRedirect("/");
      }
      return null;
    }
    AuthorizeClientIds.rememberFromReturnUrl(request, returnUrl);
    Optional<String> clientId = AuthorizeClientIds.resolve(request);
    clientId.ifPresent(id -> ExternalLoginSession.setClientId(request, id));
    String idp = firstIdp(idpParam, returnUrl);
    if (error == null && logout == null && idp != null) {
      String target = idpStartPath(idp, clientId.orElse(null));
      if (target != null) {
        response.sendRedirect(target);
        return null;
      }
    }
    boolean showGoogle = showGoogle(clientId.orElse(null));
    boolean showMicrosoft = showMicrosoft(clientId.orElse(null));
    boolean showWhatsApp = showWhatsApp(clientId.orElse(null));
    boolean showWeChat = showWeChat(clientId.orElse(null));
    String waCode = null;
    String waPrefill = null;
    if (showWhatsApp && props.whatsappLoginConfigured() && error == null) {
      WhatsAppLoginSession session = whatsappSessions.create(clientId.orElse(null));
      waCode = session.code();
      waPrefill = WhatsAppLoginMessages.prefillText(waCode);
    }
    CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrf == null) {
      csrf = (CsrfToken) request.getAttribute("_csrf");
    }
    String parameterName = csrf == null ? "_csrf" : csrf.getParameterName();
    String token = csrf == null ? "" : csrf.getToken();
    boolean genericError = error != null && (error.isBlank() || "true".equalsIgnoreCase(error));
    String errorCode = genericError ? null : error;
    return LoginPage.render(
        error != null && errorCode == null,
        logout != null,
        registered != null,
        parameterName,
        token,
        returnUrl,
        errorCode,
        showGoogle,
        showMicrosoft,
        showWhatsApp,
        showWeChat,
        waCode,
        waPrefill,
        props.getBrand().getProductName(),
        props.getBrand().getTagline(),
        props.getLogin().isAllowRegister());
  }

  private String idpStartPath(String idp, String clientId) {
    return switch (idp) {
      case "google" -> showGoogle(clientId) ? "/oauth2/authorization/google" : null;
      case "microsoft" -> showMicrosoft(clientId) ? "/oauth2/authorization/microsoft" : null;
      case "whatsapp" -> showWhatsApp(clientId) ? "/external/whatsapp" : null;
      case "wechat" -> showWeChat(clientId) ? "/external/wechat" : null;
      default -> null;
    };
  }

  /** Show when ClientProperties enable the provider (or direct-login for bare /login). */
  private boolean showGoogle(String clientId) {
    if (!props.googleLoginConfigured()) {
      return false;
    }
    if (clientId == null || clientId.isBlank()) {
      return props.getExternalLogin().getGoogle().isAllowDirectLogin();
    }
    return linker.settingsForClient(clientId).googleEnabled();
  }

  private boolean showMicrosoft(String clientId) {
    if (!props.microsoftLoginConfigured()) {
      return false;
    }
    if (clientId == null || clientId.isBlank()) {
      return props.getExternalLogin().getMicrosoft().isAllowDirectLogin();
    }
    return linker.settingsForClient(clientId).microsoftEnabled();
  }

  private boolean showWhatsApp(String clientId) {
    if (!props.whatsappLoginConfigured()) {
      return false;
    }
    if (clientId == null || clientId.isBlank()) {
      return props.getExternalLogin().getWhatsapp().isAllowDirectLogin();
    }
    return linker.settingsForClient(clientId).whatsappEnabled();
  }

  private boolean showWeChat(String clientId) {
    if (!props.wechatLoginConfigured()) {
      return false;
    }
    if (clientId == null || clientId.isBlank()) {
      return props.getExternalLogin().getWechat().isAllowDirectLogin();
    }
    return linker.settingsForClient(clientId).wechatEnabled();
  }

  static String firstIdp(String idpParam, String returnUrl) {
    String fromParam = normalizeIdp(idpParam);
    if (fromParam != null) {
      return fromParam;
    }
    return idpFromUrl(returnUrl);
  }

  static String idpFromUrl(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      String raw = url.trim();
      if (!raw.contains("://") && raw.startsWith("/")) {
        raw = "http://local.invalid" + raw;
      }
      URI uri = URI.create(raw);
      String query = uri.getRawQuery();
      if (query == null || query.isBlank()) {
        int q = url.indexOf('?');
        if (q >= 0 && q < url.length() - 1) {
          query = url.substring(q + 1);
        }
      }
      if (query == null) {
        return null;
      }
      for (String part : query.split("&")) {
        int eq = part.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String name = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
        if (!"idp".equalsIgnoreCase(name)) {
          continue;
        }
        return normalizeIdp(URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  static String normalizeIdp(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String v = raw.trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "google", "microsoft", "whatsapp", "wechat" -> v;
      default -> null;
    };
  }
}
