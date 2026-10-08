package com.myano.skoruba4j.console.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalProcessLookupTest {

  @Test
  void portFromHealthUrl() {
    assertEquals(5051, LocalProcessLookup.portOf("https://localhost:5051/health"));
    assertEquals(44302, LocalProcessLookup.portOf("https://localhost:44302/health"));
  }

  @Test
  void adminJarIsNotAdminApi() {
    assertTrue(
        LocalProcessLookup.commandLineMatches(
            "java -jar C:/x/skoruba4j-admin-0.1.0-SNAPSHOT.jar", "skoruba4j-admin"));
    assertFalse(
        LocalProcessLookup.commandLineMatches(
            "java -jar C:/x/skoruba4j-admin-api-0.1.0-SNAPSHOT.jar", "skoruba4j-admin"));
    assertTrue(
        LocalProcessLookup.commandLineMatches(
            "java -jar C:/x/skoruba4j-admin-api-0.1.0-SNAPSHOT.jar", "skoruba4j-admin-api"));
  }

  @Test
  void netstatListeningPid() {
    String netstat =
        """
        TCP    0.0.0.0:5051           0.0.0.0:0              LISTENING       19280
        TCP    127.0.0.1:15051        0.0.0.0:0              LISTENING       1
        """;
    assertEquals(Set.of(19280L), LocalProcessLookup.parseNetstatListeningPids(netstat, 5051));
  }
}
