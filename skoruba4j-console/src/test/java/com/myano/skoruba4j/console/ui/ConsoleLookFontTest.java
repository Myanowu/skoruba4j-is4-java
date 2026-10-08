package com.myano.skoruba4j.console.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.Test;

class ConsoleLookFontTest {

  @Test
  void uiFontCanDisplayChineseDialogChars() {
    ConsoleLook.install();
    Font font = ConsoleLook.ui();
    assertTrue(font.canDisplay('\u958b'), font.getFamily());
    assertTrue(font.canDisplay('\u6a94'), font.getFamily());
    assertTrue(font.canDisplay('\u53d6'), font.getFamily());
  }

  @Test
  void styleTabbedPaneInstallsSpacedChipComponents() {
    ConsoleLook.install();
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Database", new JPanel());
    tabs.addTab("TLS", new JPanel());
    ConsoleLook.styleTabbedPane(tabs);
    assertNotNull(tabs.getTabComponentAt(0));
    assertNotNull(tabs.getTabComponentAt(1));
    assertTrue(tabs.getTabComponentAt(1) instanceof JPanel);
    JPanel second = (JPanel) tabs.getTabComponentAt(1);
    assertTrue(second.getComponent(0) instanceof JLabel);
    assertTrue(((JLabel) second.getComponent(0)).getText().contains("TLS"));
  }
}
