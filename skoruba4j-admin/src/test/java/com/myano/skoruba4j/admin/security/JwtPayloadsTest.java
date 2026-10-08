package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtPayloadsTest {

  @Test
  void parseReadsPreferredUsernameAndRoleArray() {
    String jwt =
        jwt("{\"sub\":\"user-1\",\"preferred_username\":\"demo\",\"role\":[\"MyRole\"]}");
    Map<String, Object> claims = JwtPayloads.parse(jwt);
    assertEquals("user-1", claims.get("sub"));
    assertEquals("demo", claims.get("preferred_username"));
    assertTrue(claims.get("role") instanceof List<?> roles && roles.contains("MyRole"));
  }

  private static String jwt(String payloadJson) {
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return "hdr." + payload + ".sig";
  }
}
