package com.myano.skoruba4j.sts.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myano.skoruba4j.sts.config.IdserverProperties;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppCloudWebhook;
import com.myano.skoruba4j.sts.security.externallogin.WhatsAppLoginService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta WhatsApp Cloud API webhook: challenge verify + inbound text for QR LOGIN reply.
 *
 * <p>Configure callback URL to {@code https://&lt;sts-host&gt;/external/whatsapp/webhook}.
 */
@RestController
public class WhatsAppWebhookController {
  private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

  private final IdserverProperties props;
  private final WhatsAppLoginService login;
  private final ObjectMapper mapper;

  public WhatsAppWebhookController(
      IdserverProperties props, WhatsAppLoginService login, ObjectMapper mapper) {
    this.props = props;
    this.login = login;
    this.mapper = mapper;
  }

  @GetMapping(value = "/external/whatsapp/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> verify(
      @RequestParam(value = "hub.mode", required = false) String mode,
      @RequestParam(value = "hub.verify_token", required = false) String token,
      @RequestParam(value = "hub.challenge", required = false) String challenge) {
    String expected = props.getExternalLogin().getWhatsapp().getWebhookVerifyToken();
    if (!"subscribe".equals(mode)
        || expected == null
        || expected.isBlank()
        || token == null
        || !expected.equals(token)
        || challenge == null) {
      return ResponseEntity.status(403).body("forbidden");
    }
    return ResponseEntity.ok(challenge);
  }

  @PostMapping(value = "/external/whatsapp/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> inbound(
      @RequestBody String body,
      @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
    if (!props.whatsappLoginConfigured()) {
      return ResponseEntity.status(503).body("disabled");
    }
    String appSecret = props.getExternalLogin().getWhatsapp().getAppSecret();
    if (appSecret != null && !appSecret.isBlank()) {
      if (!validSignature(appSecret, body, signature)) {
        log.warn("WhatsApp webhook signature rejected");
        return ResponseEntity.status(403).body("bad signature");
      }
    }
    List<WhatsAppCloudWebhook.InboundText> messages = WhatsAppCloudWebhook.parseTexts(mapper, body);
    for (WhatsAppCloudWebhook.InboundText msg : messages) {
      try {
        login.applyInbound(msg.fromDigits(), msg.body());
      } catch (Exception e) {
        log.warn("WhatsApp login apply failed: {}", e.getMessage());
      }
    }
    return ResponseEntity.ok("EVENT_RECEIVED");
  }

  private static boolean validSignature(String appSecret, String body, String header) {
    if (header == null || !header.startsWith("sha256=")) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
      String expected = "sha256=" + toHex(digest);
      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      return false;
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
