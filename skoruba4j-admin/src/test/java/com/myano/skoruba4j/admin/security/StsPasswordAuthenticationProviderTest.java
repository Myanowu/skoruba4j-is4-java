package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class StsPasswordAuthenticationProviderTest {

  /** {@code sub} is the Identity user id used for UserRoles lookup. */
  @Test
  void subjectFromJwtReadsSubClaim() {
    String jwt = jwt("{\"sub\":\"user-42\"}");
    assertEquals("user-42", StsPasswordAuthenticationProvider.subjectFromJwt(jwt));
  }

  @Test
  void subjectFromJwtRejectsGarbage() {
    assertNull(StsPasswordAuthenticationProvider.subjectFromJwt("not-a-jwt"));
    assertNull(StsPasswordAuthenticationProvider.subjectFromJwt(null));
  }

  /** Password-grant access tokens carry role arrays from IdentityTokenClaimsCustomizer. */
  @Test
  void identityFromJwtReadsRoleArray() {
    String jwt =
        jwt(
            "{\"sub\":\"user-42\",\"preferred_username\":\"demo\",\"role\":[\"MyRole\",\"Other\"],\"roles\":[]}");
    StsPasswordAuthenticationProvider.TokenIdentity identity =
        StsPasswordAuthenticationProvider.identityFromJwt(jwt);
    assertEquals("user-42", identity.subject());
    assertTrue(identity.roleClaim() instanceof List<?> roles && roles.contains("MyRole"));
  }

  private static String jwt(String payloadJson) {
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return "hdr." + payload + ".sig";
  }
}
