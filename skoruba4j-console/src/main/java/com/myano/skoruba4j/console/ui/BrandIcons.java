package com.myano.skoruba4j.console.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** Product mark: wine plate, cream shield and keyhole — STS / identity, not a "4j" wordmark. */
public final class BrandIcons {
  public static final String PRODUCT = "Skoruba4j";
  public static final String CONSOLE_TITLE = "Skoruba4j Control";
  public static final String CONSOLE_SUBTITLE = "Processes, health, logs, and the identity store";
  public static final Color WINE = new Color(0x6B1C24);
  public static final Color CREAM = new Color(0xF0E4C8);

  private BrandIcons() {}

  public static List<Image> windowIcons() {
    BufferedImage source = loadOrPaint(256);
    return List.of(scale(source, 16), scale(source, 32), scale(source, 48), scale(source, 256));
  }

  public static ImageIcon headerIcon() {
    return new ImageIcon(scale(loadOrPaint(256), 28));
  }

  static BufferedImage loadOrPaint(int size) {
    BufferedImage loaded = loadPng();
    if (loaded != null) {
      return loaded;
    }
    return paint(size);
  }

  public static BufferedImage paint(int size) {
    int s = Math.max(16, size);
    BufferedImage image = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    float r = s * 0.23f;
    g.setColor(WINE);
    g.fill(new RoundRectangle2D.Float(0, 0, s, s, r, r));
    float cx = s / 2f;
    GeneralPath shield = new GeneralPath();
    shield.moveTo(cx, s * 0.14f);
    shield.lineTo(s * 0.82f, s * 0.30f);
    shield.lineTo(s * 0.82f, s * 0.52f);
    shield.quadTo(s * 0.82f, s * 0.76f, cx, s * 0.88f);
    shield.quadTo(s * 0.18f, s * 0.76f, s * 0.18f, s * 0.52f);
    shield.lineTo(s * 0.18f, s * 0.30f);
    shield.closePath();
    g.setColor(CREAM);
    g.fill(shield);
    g.setColor(WINE);
    float kr = s * 0.10f;
    g.fill(new Ellipse2D.Float(cx - kr, s * 0.38f, kr * 2, kr * 2));
    GeneralPath slot = new GeneralPath();
    slot.moveTo(cx - s * 0.045f, s * 0.50f);
    slot.lineTo(cx + s * 0.045f, s * 0.50f);
    slot.lineTo(cx + s * 0.07f, s * 0.70f);
    slot.lineTo(cx - s * 0.07f, s * 0.70f);
    slot.closePath();
    g.fill(slot);
    g.dispose();
    return image;
  }

  private static BufferedImage loadPng() {
    try (InputStream in = BrandIcons.class.getResourceAsStream("/brand/skoruba4j.png")) {
      if (in == null) {
        return null;
      }
      return ImageIO.read(in);
    } catch (IOException e) {
      return null;
    }
  }

  private static BufferedImage scale(BufferedImage source, int size) {
    BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.drawImage(source, 0, 0, size, size, null);
    g.dispose();
    return out;
  }
}
