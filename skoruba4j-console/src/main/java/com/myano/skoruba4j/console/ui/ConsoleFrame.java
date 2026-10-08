package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.console.config.ConsoleProperties;
import com.myano.skoruba4j.console.configfile.ConsoleUiPrefs;
import com.myano.skoruba4j.console.process.LocalProcessSupervisor;
import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.i18n.UiLocale;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/** Desktop control window: overview, process logs, settings. Maven artifact stays skoruba4j-console. */
public final class ConsoleFrame extends JFrame {
  private final Path repoRoot;
  private final CardLayout cards = new CardLayout();
  private final JPanel content = new JPanel(cards);
  private final OverviewPanel overview;
  private final SettingsPanel settings;
  private final Map<String, NodePanel> nodes = new LinkedHashMap<>();
  private final Map<String, JToggleButton> nav = new LinkedHashMap<>();
  private final JLabel brandSub = new JLabel();
  private final JLabel langLabel = new JLabel();
  private final JComboBox<UiLocale> langCombo = new JComboBox<>();
  private boolean syncingLang;

  public ConsoleFrame(
      Path repoRoot,
      Map<String, ConsoleProperties.Node> nodeMap,
      LocalProcessSupervisor supervisor) {
    super(BrandIcons.CONSOLE_TITLE);
    this.repoRoot = repoRoot;
    UiLocale.setCurrent(ConsoleUiPrefs.load(repoRoot));
    setIconImages(BrandIcons.windowIcons());
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(1080, 720));
    setSize(1180, 800);
    setLocationRelativeTo(null);
    getContentPane().setLayout(new BorderLayout());
    getContentPane().setBackground(ConsoleLook.PAPER);
    getContentPane().add(brandBar(), BorderLayout.NORTH);

    overview = new OverviewPanel(repoRoot, nodeMap, supervisor, this::showNode);
    content.add(overview, "overview");
    if (nodeMap != null) {
      for (Map.Entry<String, ConsoleProperties.Node> entry : nodeMap.entrySet()) {
        NodePanel panel = new NodePanel(entry.getKey(), entry.getValue(), repoRoot, supervisor);
        nodes.put(entry.getKey(), panel);
        content.add(panel, entry.getKey());
      }
    }
    settings = new SettingsPanel(repoRoot, this::applyUiLocale);
    content.add(settings, "settings");
    getContentPane().add(sidebar(), BorderLayout.WEST);
    getContentPane().add(content, BorderLayout.CENTER);
    Timer timer = new Timer(2000, e -> refreshVisible());
    timer.start();
    applyUiLocale(UiLocale.current());
    refreshVisible();
  }

  /** Persist and refresh chrome when the user picks a language. */
  void applyUiLocale(UiLocale locale) {
    UiLocale chosen = locale == null ? UiLocale.EN : locale;
    UiLocale.setCurrent(chosen);
    try {
      ConsoleUiPrefs.save(repoRoot, chosen);
    } catch (Exception ignored) {
      // keep in-memory locale even if prefs write fails
    }
    syncingLang = true;
    try {
      langCombo.setSelectedItem(chosen);
    } finally {
      syncingLang = false;
    }
    brandSub.setText(Messages.t("control.subtitle"));
    langLabel.setText(Messages.t("lang.choose"));
    JToggleButton overviewNav = nav.get("overview");
    if (overviewNav != null) {
      overviewNav.setText(Messages.t("control.nav.overview"));
    }
    JToggleButton settingsNav = nav.get("settings");
    if (settingsNav != null) {
      settingsNav.setText(Messages.t("control.nav.settings"));
    }
    overview.applyLocale();
    settings.applyLocale();
    revalidate();
    repaint();
  }

  private JPanel brandBar() {
    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(ConsoleLook.WINE);
    header.setBorder(new EmptyBorder(12, 20, 12, 20));
    JLabel brand = new JLabel(BrandIcons.CONSOLE_TITLE, BrandIcons.headerIcon(), JLabel.LEFT);
    brand.setForeground(ConsoleLook.CREAM);
    brand.setIconTextGap(12);
    brand.setFont(ConsoleLook.ui().deriveFont(Font.BOLD, 17f));
    brandSub.setForeground(new java.awt.Color(0xF0E4C8));
    brandSub.setFont(ConsoleLook.uiSmall());
    brandSub.setText(Messages.t("control.subtitle"));
    JPanel titles = new JPanel();
    titles.setOpaque(false);
    titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
    titles.add(brand);
    titles.add(Box.createVerticalStrut(3));
    titles.add(brandSub);
    header.add(titles, BorderLayout.WEST);

    DefaultComboBoxModel<UiLocale> model = new DefaultComboBoxModel<>();
    for (UiLocale locale : UiLocale.values()) {
      model.addElement(locale);
    }
    langCombo.setModel(model);
    langCombo.setSelectedItem(UiLocale.current());
    langCombo.setFont(ConsoleLook.uiSmall());
    langCombo.setRenderer(
        new javax.swing.DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              javax.swing.JList<?> list,
              Object value,
              int index,
              boolean isSelected,
              boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof UiLocale locale) {
              setText(locale.label());
            }
            return this;
          }
        });
    langCombo.addActionListener(
        e -> {
          if (!syncingLang && langCombo.getSelectedItem() instanceof UiLocale locale) {
            applyUiLocale(locale);
          }
        });
    langLabel.setText(Messages.t("lang.choose"));
    langLabel.setForeground(ConsoleLook.CREAM);
    langLabel.setFont(ConsoleLook.uiSmall());
    JPanel lang = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
    lang.setOpaque(false);
    lang.add(langLabel);
    lang.add(langCombo);
    header.add(lang, BorderLayout.EAST);
    return header;
  }

  private JPanel sidebar() {
    JPanel side = new JPanel();
    side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
    side.setBackground(ConsoleLook.WINE_DEEP);
    side.setBorder(new EmptyBorder(16, 10, 16, 10));
    side.setPreferredSize(new Dimension(180, 100));
    ButtonGroup group = new ButtonGroup();
    addNav(side, group, "overview", Messages.t("control.nav.overview"), true);
    for (Map.Entry<String, NodePanel> entry : nodes.entrySet()) {
      addNav(side, group, entry.getKey(), entry.getValue().displayName(), false);
    }
    addNav(side, group, "settings", Messages.t("control.nav.settings"), false);
    side.add(Box.createVerticalGlue());
    return side;
  }

  private void addNav(JPanel side, ButtonGroup group, String key, String label, boolean selected) {
    JToggleButton button = ConsoleLook.navButton(label);
    button.setAlignmentX(LEFT_ALIGNMENT);
    group.add(button);
    button.addActionListener(e -> cards.show(content, key));
    button.setSelected(selected);
    nav.put(key, button);
    side.add(button);
    side.add(Box.createVerticalStrut(4));
  }

  private void showNode(String nodeId) {
    JToggleButton button = nav.get(nodeId);
    if (button != null) {
      button.setSelected(true);
    }
    cards.show(content, nodeId);
  }

  private void refreshVisible() {
    overview.refresh();
    for (NodePanel panel : nodes.values()) {
      panel.refresh();
    }
  }
}
