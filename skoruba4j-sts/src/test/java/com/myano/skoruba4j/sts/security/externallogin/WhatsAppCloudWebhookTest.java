package com.myano.skoruba4j.sts.security.externallogin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class WhatsAppCloudWebhookTest {

  @Test
  void parsesTextMessages() {
    String json =
        """
        {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{"messages":[
          {"from":"85291234567","id":"wamid.1","timestamp":"1","type":"text","text":{"body":"LOGIN ABCD2345"}}
        ]}}]}]}
        """;
    List<WhatsAppCloudWebhook.InboundText> texts =
        WhatsAppCloudWebhook.parseTexts(new ObjectMapper(), json);
    assertEquals(1, texts.size());
    assertEquals("85291234567", texts.get(0).fromDigits());
    assertEquals("LOGIN ABCD2345", texts.get(0).body());
  }
}
