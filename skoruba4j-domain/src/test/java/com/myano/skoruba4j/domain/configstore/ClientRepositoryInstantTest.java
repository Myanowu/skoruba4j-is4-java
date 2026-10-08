package com.myano.skoruba4j.domain.configstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClientRepositoryInstantTest {

  @Test
  void parsesIsoInstant() {
    Instant expected = Instant.parse("2026-10-07T04:02:00Z");
    assertEquals(expected, ClientRepository.parseInstantText("2026-10-07T04:02:00Z"));
  }

  @Test
  void parsesSqliteTimestampWithSpace() {
    Instant parsed = ClientRepository.parseInstantText("2026-10-07 04:02:00.123");
    assertNotNull(parsed);
    assertEquals(Instant.parse("2026-10-07T04:02:00.123Z"), parsed);
  }

  @Test
  void parsesDotNetSevenDigitFraction() {
    Instant parsed = ClientRepository.parseInstantText("2022-08-25 06:53:33.7428019");
    assertNotNull(parsed);
    assertEquals(Instant.parse("2022-08-25T06:53:33.742801900Z"), parsed);
  }

  @Test
  void parsesEpochMillisDigits() {
    assertEquals(
        Instant.ofEpochMilli(1791345709024L),
        ClientRepository.parseInstantText("1791345709024"));
  }

  @Test
  void parsesDateOnly() {
    assertEquals(
        Instant.parse("2026-10-07T00:00:00Z"), ClientRepository.parseInstantText("2026-10-07"));
  }
}
