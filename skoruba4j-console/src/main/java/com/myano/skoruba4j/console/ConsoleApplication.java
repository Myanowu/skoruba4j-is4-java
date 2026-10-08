package com.myano.skoruba4j.console;

import com.myano.skoruba4j.console.config.ConsoleNodes;
import com.myano.skoruba4j.console.configfile.ConsoleUiPrefs;
import com.myano.skoruba4j.console.process.LocalProcessSupervisor;
import com.myano.skoruba4j.console.ui.ConsoleFrame;
import com.myano.skoruba4j.console.ui.ConsoleLook;
import com.myano.skoruba4j.i18n.UiLocale;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class ConsoleApplication {
  private ConsoleApplication() {}

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          setLookAndFeel();
          ConsoleLook.install();
          Path home = RepoRoot.find();
          UiLocale.setCurrent(ConsoleUiPrefs.load(home));
          ConsoleFrame frame =
              new ConsoleFrame(home, ConsoleNodes.defaults(home), new LocalProcessSupervisor());
          frame.setVisible(true);
        });
  }

  static void setLookAndFeel() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception ignored) {
      // keep default
    }
  }
}
