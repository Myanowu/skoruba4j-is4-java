package com.myano.skoruba4j.sts.security.externallogin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Minimal Meta WhatsApp Cloud API webhook parser (text messages only). */
public final class WhatsAppCloudWebhook {
  public record InboundText(String fromDigits, String body, String messageId) {}

  private WhatsAppCloudWebhook() {}

  public static List<InboundText> parseTexts(ObjectMapper mapper, String json) {
    List<InboundText> out = new ArrayList<>();
    if (mapper == null || json == null || json.isBlank()) {
      return out;
    }
    try {
      JsonNode root = mapper.readTree(json);
      JsonNode entry = root.path("entry");
      if (!entry.isArray()) {
        return out;
      }
      for (JsonNode ent : entry) {
        JsonNode changes = ent.path("changes");
        if (!changes.isArray()) {
          continue;
        }
        for (JsonNode change : changes) {
          JsonNode messages = change.path("value").path("messages");
          if (!messages.isArray()) {
            continue;
          }
          for (JsonNode msg : messages) {
            if (!"text".equalsIgnoreCase(msg.path("type").asText(""))) {
              continue;
            }
            String from = msg.path("from").asText("");
            String body = msg.path("text").path("body").asText("");
            String id = msg.path("id").asText("");
            if (!from.isBlank() && !body.isBlank()) {
              out.add(new InboundText(from.replaceAll("\\D", ""), body, id));
            }
          }
        }
      }
    } catch (Exception ignored) {
      // malformed webhook → empty
    }
    return out;
  }
}
