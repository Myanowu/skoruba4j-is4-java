package com.myano.skoruba4j.domain.configstore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiResourceAdminRepositoryAlgorithmsTest {
  @Test
  void roundTripsCommaSeparatedAlgorithms() {
    assertEquals("RS256\nPS256", ApiResourceAdminRepository.algorithmsToLines("RS256, PS256"));
    assertEquals(
        "RS256,PS256",
        ApiResourceAdminRepository.linesToAlgorithms(List.of("RS256", "PS256")));
    assertEquals("", ApiResourceAdminRepository.algorithmsToLines(null));
    assertEquals("", ApiResourceAdminRepository.linesToAlgorithms(List.of()));
  }
}
