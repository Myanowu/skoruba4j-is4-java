package com.myano.skoruba4j.console.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BrandIconsTest {

  @Test
  void paintsWinePlateWithOpaquePixels() {
    BufferedImage image = BrandIcons.paint(64);
    assertEquals(64, image.getWidth());
    int center = image.getRGB(32, 32);
    assertEquals(0xFF, (center >>> 24) & 0xFF);
    assertFalse(BrandIcons.windowIcons().isEmpty());
    assertNotNull(BrandIcons.headerIcon().getImage());
    assertEquals("Skoruba4j Control", BrandIcons.CONSOLE_TITLE);
  }
}
