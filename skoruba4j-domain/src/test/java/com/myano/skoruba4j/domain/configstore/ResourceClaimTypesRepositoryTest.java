package com.myano.skoruba4j.domain.configstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceClaimTypesRepositoryTest {

  @Test
  void allowsIsCaseInsensitive() {
    Set<String> requested = Set.of("department", "org_path");
    assertTrue(ResourceClaimTypesRepository.allows(requested, "Department"));
    assertTrue(ResourceClaimTypesRepository.allows(requested, "org_path"));
    assertFalse(ResourceClaimTypesRepository.allows(requested, "cost_center"));
    assertFalse(ResourceClaimTypesRepository.allows(Set.of(), "department"));
    assertFalse(ResourceClaimTypesRepository.allows(null, "department"));
  }

  @Test
  void lowerNormalizes() {
    assertEquals(Set.of("a", "b"), ResourceClaimTypesRepository.lower(Set.of("A", " b ")));
  }
}
