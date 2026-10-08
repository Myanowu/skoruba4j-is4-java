package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Pick a directory and file name, then create an empty SQLite file (no schema). Never overwrites.
 */
final class CreateEmptySqliteDialog extends JDialog {
  private final Path installHome;
  private final JTextField directory = new JTextField();
  private final JTextField fileName = new JTextField(SqlitePaths.FILE_NAME);
  private Path created;

  private CreateEmptySqliteDialog(Window owner, Path initialDir, Path installHome) {
    super(owner, "Create empty SQLite file", ModalityType.APPLICATION_MODAL);
    this.installHome = installHome;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(480, 200));
    getContentPane().setLayout(new BorderLayout());
    getContentPane().setBackground(ConsoleLook.PAPER);
    getContentPane().add(buildForm(initialDir), BorderLayout.CENTER);
    getContentPane().add(buttonBar(), BorderLayout.SOUTH);
    pack();
    setLocationRelativeTo(owner);
  }

  /**
   * @return absolute path of a newly created empty file, or {@code null} if cancelled / not created
   */
  static Path show(Window owner, Path initialDir, Path installHome) {
    Path start =
        initialDir != null && Files.isDirectory(initialDir)
            ? initialDir
            : SqlitePaths.file(installHome).getParent();
    CreateEmptySqliteDialog dialog = new CreateEmptySqliteDialog(owner, start, installHome);
    dialog.setVisible(true);
    return dialog.created;
  }

  private JPanel buildForm(Path initialDir) {
    JPanel form = ConsoleLook.form();
    form.setBackground(ConsoleLook.PAPER);
    form.setBorder(new EmptyBorder(12, 16, 8, 16));
    GridBagConstraints gc = ConsoleLook.formGc();

    if (initialDir != null) {
      directory.setText(initialDir.toAbsolutePath().normalize().toString().replace('\\', '/'));
    }
    JPanel dirRow = new JPanel(new BorderLayout(6, 0));
    dirRow.setOpaque(false);
    dirRow.add(directory, BorderLayout.CENTER);
    JButton browseDir = ConsoleLook.outlineButton("Browse…", ConsoleLook.WINE, ConsoleLook.WINE);
    browseDir.addActionListener(e -> browseDirectory());
    dirRow.add(browseDir, BorderLayout.EAST);
    ConsoleLook.addRow(form, gc, "Directory", dirRow);
    ConsoleLook.addRow(form, gc, "File name", fileName);

    JLabel note =
        new JLabel(
            "<html>Creates an empty .sqlite file (no tables).<br/>"
                + "Existing files are never overwritten. Schema: Manage → Initialize.</html>");
    note.setFont(ConsoleLook.uiSmall());
    note.setForeground(ConsoleLook.MUTED);
    gc.gridy++;
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    form.add(note, gc);
    return form;
  }

  private JPanel buttonBar() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    bar.setOpaque(false);
    bar.setBorder(new EmptyBorder(8, 16, 12, 16));
    JButton cancel = ConsoleLook.outlineButton("Cancel", ConsoleLook.LINE, ConsoleLook.INK);
    cancel.addActionListener(e -> dispose());
    JButton create = ConsoleLook.primary("Create");
    create.addActionListener(e -> acceptCreate());
    bar.add(cancel);
    bar.add(create);
    return bar;
  }

  private void browseDirectory() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("Choose directory for new SQLite file");
    String current = directory.getText().trim();
    if (!current.isBlank()) {
      Path dir = Path.of(current);
      if (Files.isDirectory(dir)) {
        chooser.setCurrentDirectory(dir.toFile());
      }
    } else if (installHome != null) {
      Path data = installHome.resolve("data");
      chooser.setCurrentDirectory(
          Files.isDirectory(data) ? data.toFile() : installHome.toFile());
    }
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      directory.setText(
          chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString().replace('\\', '/'));
    }
  }

  private void acceptCreate() {
    String dirText = directory.getText() == null ? "" : directory.getText().trim();
    String name = fileName.getText() == null ? "" : fileName.getText().trim();
    if (dirText.isBlank()) {
      JOptionPane.showMessageDialog(this, "Directory is required.");
      return;
    }
    if (name.isBlank()) {
      JOptionPane.showMessageDialog(this, "File name is required.");
      return;
    }
    if (name.contains("/") || name.contains("\\") || name.contains("..")) {
      JOptionPane.showMessageDialog(this, "File name must not contain path separators.");
      return;
    }
    Path dir = Path.of(dirText).toAbsolutePath().normalize();
    Path target = SqlitePaths.ensureSqliteSuffix(dir.resolve(name));
    try {
      SqlitePaths.CreateEmptyResult result = SqlitePaths.createEmptyFile(target);
      if (result == SqlitePaths.CreateEmptyResult.ALREADY_EXISTS) {
        JOptionPane.showMessageDialog(
            this,
            "File already exists — not overwritten:\n"
                + target
                + "\n\nChoose another name or directory.",
            "Create empty SQLite",
            JOptionPane.WARNING_MESSAGE);
        return;
      }
      created = target;
      dispose();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          this, e.getMessage(), "Create empty SQLite", JOptionPane.ERROR_MESSAGE);
    }
  }
}
