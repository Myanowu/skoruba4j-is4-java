package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Is4UserProfileClaimsTest {

  @Test
  void filterKeepsRequestedTypesAndCollapsesSingleValues() {
    Map<String, List<String>> raw = new LinkedHashMap<>();
    raw.put("department", List.of("Finance"));
    raw.put("org_path", List.of("/HQ/Finance"));
    raw.put("ignored", List.of("x"));
    raw.put("role", List.of("should-not-pass"));
    Map<String, Object> out =
        Is4UserProfileClaims.filter(raw, Set.of("department", "org_path"));
    assertEquals("Finance", out.get("department"));
    assertEquals("/HQ/Finance", out.get("org_path"));
    assertFalse(out.containsKey("ignored"));
    assertFalse(out.containsKey("role"));
  }

  @Test
  void filterKeepsMultiValuesAsList() {
    Map<String, List<String>> raw = Map.of("group", List.of("a", "b"));
    Map<String, Object> out = Is4UserProfileClaims.filter(raw, Set.of("group"));
    assertEquals(List.of("a", "b"), out.get("group"));
  }

  @Test
  void emptyRequestedTypesYieldsNothing() {
    Map<String, List<String>> raw = Map.of("department", List.of("Finance"));
    assertTrue(Is4UserProfileClaims.filter(raw, Set.of()).isEmpty());
  }

  @Test
  void reservedClaimTypesAreBlocked() {
    assertTrue(Is4UserProfileClaims.isReserved("sub"));
    assertTrue(Is4UserProfileClaims.isReserved("ROLE"));
    assertFalse(Is4UserProfileClaims.isReserved("department"));
  }
}
