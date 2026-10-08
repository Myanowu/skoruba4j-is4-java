package com.myano.skoruba4j.console.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Locale;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicToggleButtonUI;

/** Shared look for the desktop control window — wine/cream brand, product sans UI. */
public final class ConsoleLook {
  static final Color WINE = BrandIcons.WINE;
  static final Color WINE_DEEP = new Color(0x3D0C12);
  static final Color CREAM = BrandIcons.CREAM;
  static final Color PAPER = new Color(0xF7F3F1);
  static final Color CARD = Color.WHITE;
  static final Color LINE = new Color(0xE0D2D4);
  static final Color INK = new Color(0x241416);
  static final Color MUTED = new Color(0x6B5558);
  static final Color UP = new Color(0x1F7A45);
  static final Color DOWN = new Color(0x8A1020);

  static final int LABEL_COL = 132;
  static final int FORM_GAP_Y = 4;
  static final int FORM_GAP_X = 10;

  private static Font ui;
  private static Font uiBold;
  private static Font uiSmall;
  private static Font uiTitle;
  private static Font mono;

  private ConsoleLook() {}

  /**
   * Call once after setting LookAndFeel. Uses a CJK-capable sans so Chinese system dialogs
   * (JFileChooser) do not render as tofu boxes. Does not blanket-replace every UIManager font —
   * that breaks Windows FileChooser font linking.
   */
  public static void install() {
    ui = resolveUiFont(13f);
    uiBold = ui.deriveFont(Font.BOLD, 13f);
    uiSmall = ui.deriveFont(Font.PLAIN, 12f);
    uiTitle = ui.deriveFont(Font.BOLD, 18f);
    mono = resolveMonoFont(12f);
    FontUIResource base = new FontUIResource(ui);
    FontUIResource bold = new FontUIResource(uiBold);
    FontUIResource small = new FontUIResource(uiSmall);
    String[] keys = {
      "Label.font",
      "Button.font",
      "ToggleButton.font",
      "CheckBox.font",
      "RadioButton.font",
      "ComboBox.font",
      "TextField.font",
      "FormattedTextField.font",
      "PasswordField.font",
      "TextArea.font",
      "TextPane.font",
      "EditorPane.font",
      "Table.font",
      "TableHeader.font",
      "List.font",
      "Tree.font",
      "ToolBar.font",
      "TitledBorder.font",
      "OptionPane.messageFont",
      "OptionPane.buttonFont",
      // FileChooser must keep a CJK face — system Chinese strings live here.
      "FileChooser.font",
      "FileChooser.listFont",
    };
    for (String key : keys) {
      UIManager.put(key, base);
    }
    UIManager.put("TabbedPane.font", bold);
    // Windows L&F defaults pack tab titles into a single text row — pad them.
    UIManager.put("TabbedPane.tabInsets", new InsetsUIResource(8, 18, 8, 18));
    UIManager.put("TabbedPane.selectedTabPadInsets", new InsetsUIResource(2, 4, 2, 4));
    UIManager.put("TabbedPane.tabAreaInsets", new InsetsUIResource(6, 2, 4, 2));
    UIManager.put("TabbedPane.contentBorderInsets", new InsetsUIResource(10, 0, 0, 0));
    UIManager.put("TabbedPane.tabsOverlapBorder", Boolean.FALSE);
    UIManager.put("ToolTip.font", small);
  }

  /**
   * Settings-style tabs: chip labels with gaps so titles do not read as one run of text under
   * Windows system L&F.
   */
  static void styleTabbedPane(JTabbedPane tabs) {
    if (tabs == null) {
      return;
    }
    tabs.setFont(uiBold());
    tabs.setBackground(PAPER);
    tabs.setOpaque(true);
    tabs.setFocusable(false);
    tabs.setBorder(new EmptyBorder(0, 0, 0, 0));
    for (int i = 0; i < tabs.getTabCount(); i++) {
      String title = tabs.getTitleAt(i);
      if (title == null || title.isBlank()) {
        continue;
      }
      tabs.setTabComponentAt(i, tabChip(title, i == tabs.getSelectedIndex(), i > 0));
    }
    tabs.addChangeListener(e -> refreshTabChips(tabs));
  }

  private static void refreshTabChips(JTabbedPane tabs) {
    int sel = tabs.getSelectedIndex();
    for (int i = 0; i < tabs.getTabCount(); i++) {
      String title = tabs.getTitleAt(i);
      if (title == null || title.isBlank()) {
        continue;
      }
      tabs.setTabComponentAt(i, tabChip(title, i == sel, i > 0));
    }
  }

  private static JComponent tabChip(String title, boolean selected, boolean spaced) {
    JLabel label = new JLabel(title, SwingConstants.CENTER);
    label.setFont(uiBold());
    label.setOpaque(true);
    label.setForeground(selected ? Color.WHITE : INK);
    label.setBackground(selected ? WINE : CARD);
    label.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(selected ? WINE : LINE),
            new EmptyBorder(7, 16, 7, 16)));
    JPanel wrap = new JPanel(new java.awt.BorderLayout());
    wrap.setOpaque(false);
    wrap.setBorder(new EmptyBorder(0, spaced ? 8 : 0, 0, 0));
    wrap.add(label, java.awt.BorderLayout.CENTER);
    return wrap;
  }

  static Font ui() {
    return ui != null ? ui : resolveUiFont(13f);
  }

  static Font uiBold() {
    return uiBold != null ? uiBold : ui().deriveFont(Font.BOLD);
  }

  static Font uiSmall() {
    return uiSmall != null ? uiSmall : ui().deriveFont(Font.PLAIN, 12f);
  }

  static Font uiTitle() {
    return uiTitle != null ? uiTitle : ui().deriveFont(Font.BOLD, 18f);
  }

  static Font mono() {
    return mono != null ? mono : resolveMonoFont(12f);
  }

  static Font titleFont(Font ignored) {
    return uiTitle();
  }

  static Font mutedFont(Font ignored) {
    return uiSmall();
  }

  static JPanel paper() {
    JPanel panel = new JPanel();
    panel.setBackground(PAPER);
    panel.setOpaque(true);
    return panel;
  }

  static void card(JComponent component) {
    component.setBackground(CARD);
    component.setOpaque(true);
    component.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE), new EmptyBorder(16, 16, 16, 16)));
  }

  static JPanel form() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    panel.setBorder(new EmptyBorder(2, 2, 4, 2));
    return panel;
  }

  static GridBagConstraints formGc() {
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.insets = new Insets(FORM_GAP_Y / 2, 0, FORM_GAP_Y / 2, FORM_GAP_X);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    return gc;
  }

  static JLabel fieldLabel(String text) {
    JLabel label = new JLabel(text);
    label.setFont(ui());
    label.setForeground(INK);
    label.setPreferredSize(new Dimension(LABEL_COL, 24));
    label.setMinimumSize(new Dimension(LABEL_COL, 22));
    return label;
  }

  static void addRow(JPanel form, GridBagConstraints gc, String label, java.awt.Component field) {
    gc.gridy++;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 1;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.WEST;
    form.add(fieldLabel(label), gc);
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    if (field instanceof JComponent jc) {
      jc.setFont(ui());
    }
    form.add(field, gc);
  }

  static void addNote(JPanel form, GridBagConstraints gc, String text) {
    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JLabel note =
        new JLabel(
            "<html><body style='width:620px;font-family:\"Microsoft JhengHei UI\",\"Microsoft YaHei UI\",SansSerif;font-size:11px;color:#6B5558'>"
                + text
                + "</body></html>");
    note.setFont(uiSmall());
    note.setForeground(MUTED);
    note.setBorder(new EmptyBorder(0, 0, 2, 0));
    form.add(note, gc);
    gc.gridwidth = 1;
  }

  static void addSection(JPanel form, GridBagConstraints gc, String title) {
    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JLabel section = new JLabel(title);
    section.setFont(uiBold());
    section.setForeground(WINE);
    boolean first = gc.gridy <= 1;
    section.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
            new EmptyBorder(first ? 0 : 8, 0, 4, 0)));
    form.add(section, gc);
    gc.gridwidth = 1;
  }

  static JToggleButton navButton(String text) {
    JToggleButton button =
        new JToggleButton(text) {
          @Override
          public void updateUI() {
            setUI(new BasicToggleButtonUI());
          }

          @Override
          protected void paintComponent(Graphics g) {
            ButtonModel model = getModel();
            Color bg;
            Color fg;
            if (model.isSelected()) {
              bg = CREAM;
              fg = WINE_DEEP;
            } else if (model.isRollover() || model.isArmed()) {
              bg = WINE;
              fg = Color.WHITE;
            } else {
              bg = WINE_DEEP;
              fg = CREAM;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(fg);
            g2.setFont(ui());
            FontMetrics fm = g2.getFontMetrics();
            int x = 14;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), x, y);
            g2.dispose();
          }
        };
    button.setHorizontalAlignment(AbstractButton.LEFT);
    button.setFocusPainted(false);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setOpaque(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setBorder(new EmptyBorder(9, 14, 9, 14));
    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
    button.setPreferredSize(new Dimension(168, 38));
    button.setFont(ui());
    return button;
  }

  static JButton primary(String text) {
    return solidButton(text, WINE, Color.WHITE);
  }

  private static final String PROP_FILL = "skoruba4j.fill";
  private static final String PROP_TEXT = "skoruba4j.text";
  private static final String PROP_EDGE = "skoruba4j.edge";
  private static final String PROP_OUTLINE = "skoruba4j.outline";

  /** Filled action — Start / primary. */
  static JButton solidButton(String text, Color background, Color foreground) {
    JButton button = paintedButton(text);
    applySolid(button, background, foreground);
    return button;
  }

  /** Outlined action — Stop / secondary. */
  static JButton outlineButton(String text, Color border, Color foreground) {
    JButton button = paintedButton(text);
    applyOutline(button, border, foreground);
    return button;
  }

  static JButton dangerOutline(String text) {
    return outlineButton(text, DOWN, DOWN);
  }

  /** Switch an existing painted button to filled style (e.g. available primary action). */
  static void applySolid(JButton button, Color background, Color foreground) {
    if (button == null) {
      return;
    }
    button.putClientProperty(PROP_FILL, background);
    button.putClientProperty(PROP_TEXT, foreground);
    button.putClientProperty(PROP_EDGE, background);
    button.putClientProperty(PROP_OUTLINE, Boolean.FALSE);
    button.repaint();
  }

  /** Switch an existing painted button to outlined style. */
  static void applyOutline(JButton button, Color border, Color foreground) {
    if (button == null) {
      return;
    }
    button.putClientProperty(PROP_FILL, CARD);
    button.putClientProperty(PROP_TEXT, foreground);
    button.putClientProperty(PROP_EDGE, border);
    button.putClientProperty(PROP_OUTLINE, Boolean.TRUE);
    button.repaint();
  }

  private static JButton paintedButton(String text) {
    JButton button =
        new JButton(text) {
          @Override
          public void updateUI() {
            setUI(new javax.swing.plaf.basic.BasicButtonUI());
          }

          @Override
          protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean hover = getModel().isRollover() || getModel().isArmed();
            boolean enabled = isEnabled();
            boolean outline = Boolean.TRUE.equals(getClientProperty(PROP_OUTLINE));
            Color bg = colorProp(PROP_FILL, outline ? CARD : WINE);
            Color fg = colorProp(PROP_TEXT, outline ? WINE : Color.WHITE);
            Color bd = colorProp(PROP_EDGE, outline ? WINE : WINE);
            Color fill = enabled ? bg : new Color(0xE8E0E1);
            Color text = enabled ? fg : MUTED;
            Color edge = enabled ? bd : LINE;
            if (hover && enabled) {
              fill = outline ? new Color(0xF7F0F1) : bg.darker();
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            if (outline || hover) {
              g2.setColor(edge);
              g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g2.setColor(text);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), x, y);
            g2.dispose();
          }

          private Color colorProp(String key, Color fallback) {
            Object value = getClientProperty(key);
            return value instanceof Color c ? c : fallback;
          }
        };
    button.setFocusPainted(false);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setOpaque(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setFont(uiBold());
    button.setBorder(new EmptyBorder(8, 16, 8, 16));
    button.setPreferredSize(new Dimension(112, 34));
    button.setMinimumSize(new Dimension(88, 32));
    return button;
  }

  private static Font resolveUiFont(float size) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    // Prefer CJK faces first on Windows — Segoe UI alone often draws Chinese as □ in Swing.
    String[] candidates =
        os.contains("win")
            ? new String[] {
              "Microsoft JhengHei UI",
              "Microsoft YaHei UI",
              "Microsoft JhengHei",
              "Microsoft YaHei",
              "Segoe UI",
              "Arial"
            }
            : os.contains("mac")
                ? new String[] {"PingFang TC", "PingFang SC", "Hiragino Sans GB", "Helvetica Neue", "Arial"}
                : new String[] {"Noto Sans CJK TC", "Noto Sans CJK SC", "Noto Sans", "DejaVu Sans", "SansSerif"};
    for (String name : candidates) {
      Font font = new Font(name, Font.PLAIN, Math.round(size));
      if (!isLogicalFallback(font, name) && canDisplayChinese(font)) {
        return font.deriveFont(size);
      }
    }
    for (String name : candidates) {
      Font font = new Font(name, Font.PLAIN, Math.round(size));
      if (!isLogicalFallback(font, name)) {
        return font.deriveFont(size);
      }
    }
    return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));
  }

  private static boolean canDisplayChinese(Font font) {
    // 開 / 檔 / 取 — common FileChooser label characters
    return font != null
        && font.canDisplay('\u958b')
        && font.canDisplay('\u6a94')
        && font.canDisplay('\u53d6');
  }

  private static Font resolveMonoFont(float size) {
    String[] candidates = {"Cascadia Mono", "Consolas", "JetBrains Mono", "Monospaced"};
    for (String name : candidates) {
      Font font = new Font(name, Font.PLAIN, Math.round(size));
      if (!isLogicalFallback(font, name)) {
        return font.deriveFont(size);
      }
    }
    return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
  }

  private static boolean isLogicalFallback(Font font, String requested) {
    String family = font.getFamily();
    if (family == null) {
      return true;
    }
    String f = family.toLowerCase(Locale.ROOT);
    String r = requested.toLowerCase(Locale.ROOT);
    if (f.equals(r)) {
      return false;
    }
    // AWT substitutes Dialog/SansSerif when the face is missing.
    return f.contains("dialog") || f.equals("sansserif") || f.equals("serif") || f.equals("monospaced");
  }
}
