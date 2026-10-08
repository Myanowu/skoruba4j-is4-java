package com.myano.skoruba4j.console.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class LogTailer {
  private LogTailer() {}

  public static String tail(Path file, int maxChars) throws IOException {
    return tailSkipping(file, 0, maxChars);
  }

  public static String tailSkipping(Path file, long skipBytes, int maxChars) throws IOException {
    if (file == null || !Files.exists(file)) {
      return "";
    }
    long size = Files.size(file);
    long from = Math.min(Math.max(0L, skipBytes), size);
    try (InputStream in = Files.newInputStream(file)) {
      in.skipNBytes(from);
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (maxChars <= 0 || text.length() <= maxChars) {
        return text;
      }
      return text.substring(text.length() - maxChars);
    }
  }

  public static long size(Path file) {
    try {
      return file != null && Files.exists(file) ? Files.size(file) : 0L;
    } catch (IOException e) {
      return 0L;
    }
  }

  public static void truncate(Path file) throws IOException {
    if (file == null || !Files.exists(file)) {
      return;
    }
    try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
      ch.truncate(0);
    }
  }
}
