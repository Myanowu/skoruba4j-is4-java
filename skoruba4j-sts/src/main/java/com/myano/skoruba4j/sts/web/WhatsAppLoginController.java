package com.myano.skoruba4j.sts.web;

import com.myano.skoruba4j.domain.externallogin.ExternalLoginClientSettings;
import com.myano.skoruba4j.domain.externallogin.PhoneNumbers;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.sts.config.IdserverProperties;
import com.myano.skoruba4j.sts.security.IdentityUserDetailsService;
import com.myano.skoruba4j.sts.security.Is4LoginSuccessHandler;
import com.myano.skoruba4j.sts.security.externallogin.AuthorizeClientIds;
import com.myano.skoruba4j.sts.security.externallogin.ExternalIdentityLinker;
import com.myano.skoruba4j.sts.security.externallogin.ExternalLoginSession;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppLoginMessages;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppLoginSession;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppLoginSessionStore;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppQrImages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WhatsAppLoginController {
  private final IdserverProperties props;
  private final ExternalIdentityLinker linker;
  private final WhatsAppLoginSessionStore store;
  private final IdentityUserDetailsService users;
  private final Optional<JdbcRepositories> jdbc;
  private final Is4LoginSuccessHandler resume;

  public WhatsAppLoginController(
      IdserverProperties props,
      ExternalIdentityLinker linker,
      WhatsAppLoginSessionStore store,
      IdentityUserDetailsService users,
      Optional<JdbcRepositories> jdbc,
      RequestCache requestCache,
      SecurityContextRepository securityContextRepository) {
    this.props = props;
    this.linker = linker;
    this.store = store;
    this.users = users;
    this.jdbc = jdbc;
    this.resume = new Is4LoginSuccessHandler(securityContextRepository);
    this.resume.setRequestCache(requestCache);
  }

  @GetMapping(value = "/external/whatsapp", produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String start(HttpServletRequest request) {
    Optional<String> clientId = AuthorizeClientIds.resolve(request);
    clientId.ifPresent(id -> ExternalLoginSession.setClientId(request, id));
    String resolved = clientId.orElse(ExternalLoginSession.clientId(request));
    if (!allowWhatsApp(resolved)) {
      return redirectLogin("external-disabled");
    }
    if (!props.whatsappLoginConfigured()) {
      return setupNeeded();
    }
    WhatsAppLoginSession session = store.create(resolved);
    String business = PhoneNumbers.waMeNumber(props.getExternalLogin().getWhatsapp().getBusinessPhone());
    String prefill = WhatsAppLoginMessages.prefillText(session.code());
    String waMe =
        "https://wa.me/"
            + business
            + "?text="
            + URLEncoder.encode(prefill, StandardCharsets.UTF_8);
    StringBuilder body = new StringBuilder();
    body.append("<p class=\"lead\">Scan with WhatsApp, then send the pre-filled LOGIN message.</p>");
    body.append("<p class=\"wa-qr\"><img alt=\"WhatsApp login QR\" width=\"240\" height=\"240\" src=\"")
        .append(LoginPage.esc("/external/whatsapp/qr.png?code=" + session.code()))
        .append("\"></p>");
    body.append("<p class=\"muted\">Or open <a href=\"")
        .append(LoginPage.esc(waMe))
        .append("\">WhatsApp</a> and send: <code>")
        .append(LoginPage.esc(prefill))
        .append("</code></p>");
    body.append("<p id=\"waStatus\" class=\"muted\">Waiting for your WhatsApp reply…</p>");
    body.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/login\">Cancel</a></p>");
    body.append("<script>");
    body.append("(function(){var code=")
        .append(jsString(session.code()))
        .append(";");
    body.append("function tick(){fetch('/external/whatsapp/status?code='+encodeURIComponent(code),{credentials:'same-origin'})");
    body.append(".then(function(r){return r.json();}).then(function(j){");
    body.append("var el=document.getElementById('waStatus');");
    body.append("if(!j||!j.status){return;}");
    body.append("if(j.status==='ready'||j.status==='need_confirm'){");
    body.append("if(el)el.textContent='Verified — signing you in…';");
    body.append("window.location='/external/whatsapp/complete?code='+encodeURIComponent(code);return;}");
    body.append("if(j.status==='failed'){if(el)el.textContent='Sign-in failed.';");
    body.append("window.location='/login?error='+encodeURIComponent(j.error||'external');return;}");
    body.append("if(j.status==='expired'){if(el)el.textContent='QR expired.';");
    body.append("window.location='/login?error=external-no-pending';return;}");
    body.append("setTimeout(tick,1500);}).catch(function(){setTimeout(tick,2000);});}");
    body.append("setTimeout(tick,1200);})();");
    body.append("</script>");
    return StsPages.document(
        "WhatsApp sign-in",
        "auth",
        StsPages.card(
            "Skoruba4j STS",
            "Sign in with WhatsApp",
            "Reply LOGIN from the number on your account.",
            body.toString()));
  }

  @GetMapping(value = "/external/whatsapp/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> qr(@RequestParam("code") String code) {
    if (!props.whatsappLoginConfigured()) {
      return ResponseEntity.notFound().build();
    }
    Optional<WhatsAppLoginSession> session = store.find(code);
    if (session.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    String business = PhoneNumbers.waMeNumber(props.getExternalLogin().getWhatsapp().getBusinessPhone());
    String prefill = WhatsAppLoginMessages.prefillText(session.get().code());
    String waMe =
        "https://wa.me/"
            + business
            + "?text="
            + URLEncoder.encode(prefill, StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .body(WhatsAppQrImages.png(waMe, 240));
  }

  @GetMapping(value = "/external/whatsapp/status", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Map<String, String> status(@RequestParam("code") String code) {
    Map<String, String> json = new LinkedHashMap<>();
    Optional<WhatsAppLoginSession> found = store.find(code);
    if (found.isEmpty()) {
      json.put("status", "expired");
      return json;
    }
    WhatsAppLoginSession session = found.get();
    json.put(
        "status",
        switch (session.status()) {
          case PENDING -> "pending";
          case READY -> "ready";
          case NEED_CONFIRM -> "need_confirm";
          case FAILED -> "failed";
          case CONSUMED -> "consumed";
        });
    if (session.errorKey() != null) {
      json.put("error", session.errorKey());
    }
    return json;
  }

  @GetMapping("/external/whatsapp/complete")
  public void complete(
      HttpServletRequest request, HttpServletResponse response, @RequestParam("code") String code)
      throws IOException {
    Optional<WhatsAppLoginSession> found = store.find(code);
    if (found.isEmpty()) {
      response.sendRedirect("/login?error=external-no-pending");
      return;
    }
    WhatsAppLoginSession session = found.get();
    if (session.clientId() != null && !session.clientId().isBlank()) {
      ExternalLoginSession.setClientId(request, session.clientId());
    }
    if (session.status() == WhatsAppLoginSession.Status.NEED_CONFIRM) {
      if (!session.consume()) {
        response.sendRedirect("/login?error=external-no-pending");
        return;
      }
      ExternalLoginSession.setPending(request, session.pending());
      store.remove(session.code());
      response.sendRedirect("/external/confirm");
      return;
    }
    if (session.status() != WhatsAppLoginSession.Status.READY
        || session.userId() == null
        || session.userId().isBlank()) {
      if (session.status() == WhatsAppLoginSession.Status.FAILED) {
        response.sendRedirect(
            "/login?error="
                + URLEncoder.encode(
                    session.errorKey() == null ? "external" : session.errorKey(),
                    StandardCharsets.UTF_8));
        return;
      }
      response.sendRedirect("/external/whatsapp");
      return;
    }
    if (!session.consume()) {
      response.sendRedirect("/login?error=external-no-pending");
      return;
    }
    Optional<IdentityUser> user =
        jdbc.flatMap(j -> j.users().findById(session.userId()));
    store.remove(session.code());
    if (user.isEmpty()) {
      response.sendRedirect("/login?error=external-no-account");
      return;
    }
    UserDetails details = users.userDetailsFor(user.get());
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            details, details.getPassword(), details.getAuthorities());
    try {
      resume.onAuthenticationSuccess(request, response, token);
    } catch (Exception e) {
      response.sendRedirect("/login?error=external");
    }
  }

  private boolean allowWhatsApp(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      return props.getExternalLogin().getWhatsapp().isAllowDirectLogin();
    }
    ExternalLoginClientSettings settings = linker.settingsForClient(clientId);
    return settings.whatsappEnabled();
  }

  private static String setupNeeded() {
    StringBuilder body = new StringBuilder();
    body.append(
        "<p class=\"lead\">WhatsApp QR needs a business phone on STS before a QR can be shown.</p>");
    body.append("<ol class=\"muted\" style=\"padding-left:1.2rem\">");
    body.append(
        "<li>Control → Settings → <strong>External IdP</strong> → WhatsApp → Business phone</li>");
    body.append("<li>Set webhook verify token; Meta callback <code>/external/whatsapp/webhook</code></li>");
    body.append("<li>Save, restart STS (and Admin API if you use its login buttons)</li>");
    body.append("<li>Admin → Clients → enable <strong>Allow WhatsApp QR login</strong></li>");
    body.append("</ol>");
    body.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/login\">Back to sign in</a></p>");
    return StsPages.document(
        "WhatsApp setup",
        "auth",
        StsPages.card("Skoruba4j STS", "Configure WhatsApp", null, body.toString()));
  }

  private static String redirectLogin(String code) {
    return "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url=/login?error="
        + LoginPage.esc(code)
        + "\"></head><body></body></html>";
  }

  private static String jsString(String value) {
    if (value == null) {
      return "''";
    }
    return "'"
        + value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        + "'";
  }
}
