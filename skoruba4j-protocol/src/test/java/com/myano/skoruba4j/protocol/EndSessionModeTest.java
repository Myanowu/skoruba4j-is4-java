package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EndSessionModeTest {

  @Test
  void defaultsToCompatibleForExistingIs4Clients() {
    assertEquals(EndSessionMode.COMPATIBLE, EndSessionMode.fromConfig(null));
    assertEquals(EndSessionMode.COMPATIBLE, EndSessionMode.fromConfig(""));
    assertEquals(EndSessionMode.COMPATIBLE, EndSessionMode.fromConfig("compatible"));
    assertTrue(EndSessionMode.fromConfig("compatible").isCompatible());
  }

  @Test
  void strictIsOptIn() {
    assertEquals(EndSessionMode.STRICT, EndSessionMode.fromConfig("strict"));
    assertEquals(EndSessionMode.STRICT, EndSessionMode.fromConfig("STRICT"));
    assertFalse(EndSessionMode.fromConfig("strict").isCompatible());
    assertTrue(EndSessionMode.fromConfig("strict").isStrict());
  }
}
