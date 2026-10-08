package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.domain.jdbc.IdentityStoreRowEditor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** Compact emergency single-row editor. Primary keys are read-only. */
final class RowEditDialog extends JDialog {
  private final IdentityStoreRowEditor.RowModel original;
  private final Map<String, JTextField> fields = new LinkedHashMap<>();
  private JPasswordField newPassword;
  private Map<String, String> acceptedProposed;
  private String acceptedPassword;

  private RowEditDialog(Window owner, IdentityStoreRowEditor.RowModel row) {
    // Must nest under the Manage dialog (also modal). Owning the main Frame freezes UI:
    // this editor opens behind Manage and "Loading row…" never clears.
    super(owner, "Edit row — " + row.table(), Dialog.ModalityType.DOCUMENT_MODAL);
    this.original = row;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    JPanel form = new JPanel(new GridBagLayout());
    form.setBorder(new EmptyBorder(8, 10, 4, 10));
    form.setBackground(ConsoleLook.PAPER);
    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 0, 2, 8);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.anchor = GridBagConstraints.EAST;
    int y = 0;
    for (IdentityStoreRowEditor.ColumnInfo column : row.columns()) {
      if ("PasswordHash".equalsIgnoreCase(column.name())) {
        continue;
      }
      gc.gridy = y;
      gc.gridx = 0;
      gc.weightx = 0;
      JLabel label = new JLabel(column.name());
      label.setFont(ConsoleLook.uiSmall());
      if (column.primaryKey()) {
        label.setForeground(ConsoleLook.MUTED);
      }
      form.add(label, gc);
      gc.gridx = 1;
      gc.weightx = 1;
      JTextField field = new JTextField(row.values().getOrDefault(column.name(), ""), 26);
      field.setFont(ConsoleLook.uiSmall());
      field.setMargin(new Insets(2, 4, 2, 4));
      if (column.primaryKey()) {
        field.setEditable(false);
        field.setBackground(ConsoleLook.CREAM);
      } else if (column.sensitive()) {
        field.setText("");
        field.setToolTipText("Leave blank to keep the current secret.");
      }
      fields.put(column.name(), field);
      form.add(field, gc);
      y++;
    }
    boolean hasPasswordHash =
        row.columns().stream().anyMatch(c -> "PasswordHash".equalsIgnoreCase(c.name()));
    if (hasPasswordHash) {
      gc.gridy = y;
      gc.gridx = 0;
      gc.weightx = 0;
      JLabel label = new JLabel("New password");
      label.setFont(ConsoleLook.uiSmall());
      form.add(label, gc);
      gc.gridx = 1;
      gc.weightx = 1;
      newPassword = new JPasswordField(26);
      newPassword.setFont(ConsoleLook.uiSmall());
      newPassword.setMargin(new Insets(2, 4, 2, 4));
      newPassword.setToolTipText("Optional. Blank keeps PasswordHash.");
      form.add(newPassword, gc);
      y++;
    }

    JScrollPane scroll = new JScrollPane(form);
    scroll.setBorder(javax.swing.BorderFactory.createLineBorder(ConsoleLook.LINE));
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    int visible = Math.min(Math.max(y, 4), 14);
    scroll.setPreferredSize(new Dimension(400, 16 + visible * 26));

    JLabel note =
        new JLabel(
            "<html><body style='width:360px'>Emergency edit. PK locked · blank secret = keep · "
                + "prefer Admin for routine changes.</body></html>");
    note.setFont(ConsoleLook.uiSmall());
    note.setForeground(ConsoleLook.MUTED);
    note.setBorder(new EmptyBorder(8, 10, 2, 10));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    buttons.setBorder(new EmptyBorder(6, 10, 10, 10));
    JButton cancel = ConsoleLook.outlineButton("Cancel", ConsoleLook.LINE, ConsoleLook.INK);
    JButton save = ConsoleLook.primary("Save");
    cancel.addActionListener(e -> dispose());
    save.addActionListener(e -> onSave());
    buttons.add(cancel);
    buttons.add(save);

    JPanel root = new JPanel(new BorderLayout(0, 0));
    root.setBackground(ConsoleLook.PAPER);
    root.add(note, BorderLayout.NORTH);
    root.add(scroll, BorderLayout.CENTER);
    root.add(buttons, BorderLayout.SOUTH);
    setContentPane(root);
    pack();
    setResizable(true);
    setMinimumSize(new Dimension(420, 220));
    setLocationRelativeTo(owner);
  }

  record Result(Map<String, String> proposed, String newPassword) {}

  static Result open(Window owner, IdentityStoreRowEditor.RowModel row) {
    RowEditDialog dialog = new RowEditDialog(owner, row);
    dialog.setVisible(true);
    if (dialog.acceptedProposed == null) {
      return null;
    }
    return new Result(dialog.acceptedProposed, dialog.acceptedPassword);
  }

  private void onSave() {
    Map<String, String> proposed = new LinkedHashMap<>();
    for (Map.Entry<String, JTextField> e : fields.entrySet()) {
      proposed.put(e.getKey(), e.getValue().getText());
    }
    String pwd = newPassword == null ? "" : new String(newPassword.getPassword());
    StringBuilder changes = new StringBuilder();
    for (IdentityStoreRowEditor.ColumnInfo column : original.columns()) {
      if (column.primaryKey() || "PasswordHash".equalsIgnoreCase(column.name())) {
        continue;
      }
      String next = proposed.get(column.name());
      if (column.sensitive()) {
        if (next != null && !next.isBlank()) {
          changes.append("• ").append(column.name()).append(" (new secret)\n");
        }
        continue;
      }
      String prev = original.values().getOrDefault(column.name(), "");
      if (!prev.equals(next == null ? "" : next)) {
        changes.append("• ").append(column.name()).append('\n');
      }
    }
    if (!pwd.isBlank()) {
      changes.append("• PasswordHash (new password)\n");
    }
    if (changes.length() == 0) {
      JOptionPane.showMessageDialog(this, "No changes.");
      return;
    }
    int ok =
        JOptionPane.showConfirmDialog(
            this,
            "Update " + original.table() + "?\n\n" + changes + "\nEmergency write.",
            "Confirm edit",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (ok != JOptionPane.OK_OPTION) {
      return;
    }
    acceptedProposed = proposed;
    acceptedPassword = pwd;
    dispose();
  }
}
