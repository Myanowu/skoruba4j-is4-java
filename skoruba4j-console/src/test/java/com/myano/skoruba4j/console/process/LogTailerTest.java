package com.myano.skoruba4j.console.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogTailerTest {

  @Test
  void returnsEmptyWhenMissing(@TempDir Path dir) throws Exception {
    assertEquals("", LogTailer.tail(dir.resolve("missing.log"), 100));
  }

  @Test
  void keepsOnlyTheTail(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("app.log");
    Files.writeString(file, "abcdefghij");
    assertEquals("hij", LogTailer.tail(file, 3));
    assertTrue(LogTailer.tail(file, 100).contains("abc"));
  }

  @Test
  void truncateThenSkipHidesOldBytes(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("app.log");
    Files.writeString(file, "old-line\n");
    LogTailer.truncate(file);
    assertEquals("", LogTailer.tail(file, 100));
    Files.writeString(file, "new-line\n");
    assertEquals("new-line\n", LogTailer.tailSkipping(file, 0, 100));
    Files.writeString(file, "keep-this");
    assertEquals("this", LogTailer.tailSkipping(file, 5, 100));
  }
}
