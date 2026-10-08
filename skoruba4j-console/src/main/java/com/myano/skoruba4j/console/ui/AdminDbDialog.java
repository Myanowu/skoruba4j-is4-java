package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import com.myano.skoruba4j.console.jdbc.JdbcClientChoices;
import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.jdbc.IdentityStoreAdmin;
import com.myano.skoruba4j.domain.jdbc.IdentityStoreCopy;
import com.myano.skoruba4j.domain.jdbc.IdentityStoreRowEditor;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javax.sql.DataSource;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * Ops console for one named identity-store connection: read-only table explore (left list + row
 * preview) and emergency backup/restore. Not a replacement for skoruba4j-admin.
 */
final class AdminDbDialog extends JDialog {
  private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final int DEFAULT_PREVIEW_LIMIT = 100;

  private final Path installHome;
  private final Supplier<LocalConfigFile.Form> settings;
  private final String connectionName;
  private final DefaultListModel<TableItem> tableModel = new DefaultListModel<>();
  private final JList<TableItem> tableList = new JList<>(tableModel);
  private final JTextField tableFilter = new JTextField();
  private final DefaultTableModel dataModel =
      new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  private final JTable dataTable = new JTable(dataModel);
  private final JLabel summary = new JLabel(" ");
  private final JLabel currentBanner = new JLabel(" ");
  private final JLabel previewStatus = new JLabel("Select a table");
  private final JTextArea help = new JTextArea();
  private final JTextArea log = new JTextArea(4, 64);
  private final JSpinner limitSpinner =
      new JSpinner(new SpinnerNumberModel(DEFAULT_PREVIEW_LIMIT, 1, 10_000, 50));
  private final JSpinner offsetSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 100));
  private final JComboBox<String> orderBy = new JComboBox<>();
  private final JCheckBox revealSecrets = new JCheckBox("Show secrets");
  private final JButton refresh = new JButton("Refresh");
  private final JButton initialize = new JButton("Initialize");
  private final JButton firstLoginHelp = new JButton("First login…");
  private final JButton backup = new JButton("Backup");
  private final JButton restore = new JButton("Restore");
  private boolean showLoginHelpAfterInit;
  private final JButton reloadRows = new JButton("Reload");
  private final JButton editRow = new JButton("Edit row…");
  private final JButton prevPage = new JButton("Prev");
  private final JButton nextPage = new JButton("Next");
  private volatile String extraLog = "";
  private volatile boolean busy;
  private volatile boolean loadingPreview;
  private volatile boolean syncingPreviewControls;
  private boolean sortAscending = true;
  private String sortColumn = "";
  private Path pendingBackup;
  private Path pendingRestore;
  private String selectedTable = "";
  private List<IdentityStoreAdmin.TableStatus> lastTables = List.of();

  private AdminDbDialog(
      Frame owner, Path installHome, String connectionName, Supplier<LocalConfigFile.Form> settings) {
    super(owner, titleFor(connectionName, settings.get()), true);
    this.installHome = installHome;
    this.settings = settings;
    this.connectionName = connectionName == null ? "" : connectionName.trim();
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    applyChrome(settings.get());

    JPanel root = new JPanel(new BorderLayout(8, 8));
    root.setBorder(new EmptyBorder(12, 12, 12, 12));

    JPanel north = new JPanel(new BorderLayout(4, 4));
    help.setEditable(false);
    help.setLineWrap(true);
    help.setWrapStyleWord(true);
    help.setOpaque(false);
    help.setFont(ConsoleLook.uiSmall());
    currentBanner.setFont(ConsoleLook.uiBold());
    currentBanner.setForeground(ConsoleLook.WINE);
    north.add(currentBanner, BorderLayout.NORTH);
    north.add(help, BorderLayout.CENTER);
    north.add(summary, BorderLayout.SOUTH);
    root.add(north, BorderLayout.NORTH);

    tableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    tableList.setFont(ConsoleLook.ui());
    tableList.setFixedCellWidth(220);
    tableList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) {
            onTableSelected();
          }
        });
    tableFilter.putClientProperty("JTextField.placeholderText", "Filter tables…");
    tableFilter
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                rebuildTableList(selectedTable);
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                rebuildTableList(selectedTable);
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                rebuildTableList(selectedTable);
              }
            });
    JScrollPane leftScroll = new JScrollPane(tableList);
    leftScroll.setPreferredSize(new Dimension(240, 360));
    JPanel left = new JPanel(new BorderLayout(0, 4));
    JLabel leftTitle = new JLabel("Tables");
    leftTitle.setFont(ConsoleLook.uiBold());
    left.add(leftTitle, BorderLayout.NORTH);
    JPanel leftCenter = new JPanel(new BorderLayout(0, 4));
    leftCenter.add(tableFilter, BorderLayout.NORTH);
    leftCenter.add(leftScroll, BorderLayout.CENTER);
    left.add(leftCenter, BorderLayout.CENTER);

    dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    dataTable.setFont(ConsoleLook.ui().deriveFont(java.awt.Font.PLAIN, 11f));
    dataTable.setRowHeight(18);
    dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    dataTable.setAutoCreateRowSorter(false);
    JTableHeader header = dataTable.getTableHeader();
    header.setFont(ConsoleLook.ui().deriveFont(java.awt.Font.BOLD, 11f));
    header.setReorderingAllowed(false);
    header.setToolTipText("Click column header to sort. Click again to reverse.");
    header.setDefaultRenderer(
        new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(
              JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c =
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (c instanceof JLabel label) {
              String name = value == null ? "" : String.valueOf(value);
              if (!sortColumn.isBlank() && name.equalsIgnoreCase(sortColumn)) {
                label.setText(name + (sortAscending ? " ▲" : " ▼"));
                label.setForeground(ConsoleLook.WINE);
              } else {
                label.setText(name);
                label.setForeground(ConsoleLook.INK);
              }
              label.setFont(ConsoleLook.ui().deriveFont(java.awt.Font.BOLD, 11f));
              label.setHorizontalAlignment(LEFT);
              label.setBorder(new EmptyBorder(2, 4, 2, 4));
              label.setBackground(ConsoleLook.CREAM);
              label.setOpaque(true);
            }
            return c;
          }
        });
    header.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            int col = dataTable.columnAtPoint(e.getPoint());
            if (col < 0 || busy || loadingPreview) {
              return;
            }
            String name = dataTable.getColumnName(col);
            if (name == null || name.isBlank()) {
              return;
            }
            if (name.equalsIgnoreCase(sortColumn)) {
              sortAscending = !sortAscending;
            } else {
              sortColumn = name;
              sortAscending = true;
              syncingPreviewControls = true;
              try {
                orderBy.setSelectedItem(name);
              } finally {
                syncingPreviewControls = false;
              }
            }
            offsetSpinner.setValue(0);
            loadPreview(selectedTable);
          }
        });
    dataTable.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && dataTable.getSelectedRow() >= 0) {
              editSelectedRow();
            }
          }
        });
    JScrollPane rightScroll = new JScrollPane(dataTable);
    rightScroll.setPreferredSize(new Dimension(640, 360));

    orderBy.setEditable(false);
    orderBy.setPreferredSize(new Dimension(120, 24));
    orderBy.addActionListener(
        e -> {
          if (!syncingPreviewControls) {
            String selected = (String) orderBy.getSelectedItem();
            if (selected != null && !selected.equalsIgnoreCase(sortColumn)) {
              sortColumn = selected;
              sortAscending = true;
            }
            loadPreview(selectedTable);
          }
        });
    revealSecrets.addActionListener(e -> loadPreview(selectedTable));
    prevPage.addActionListener(e -> pageBy(-pageSize()));
    nextPage.addActionListener(e -> pageBy(pageSize()));

    JPanel previewBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    previewBar.setOpaque(false);
    previewBar.add(new JLabel("Offset"));
    offsetSpinner.setPreferredSize(new Dimension(72, 24));
    previewBar.add(offsetSpinner);
    previewBar.add(new JLabel("Rows"));
    limitSpinner.setPreferredSize(new Dimension(72, 24));
    previewBar.add(limitSpinner);
    previewBar.add(new JLabel("Order"));
    previewBar.add(orderBy);
    previewBar.add(prevPage);
    previewBar.add(nextPage);
    reloadRows.addActionListener(e -> loadPreview(selectedTable));
    editRow.addActionListener(e -> editSelectedRow());
    previewBar.add(reloadRows);
    previewBar.add(editRow);
    previewBar.add(revealSecrets);
    previewStatus.setFont(ConsoleLook.uiSmall());
    previewStatus.setForeground(ConsoleLook.MUTED);
    previewBar.add(previewStatus);

    JPanel right = new JPanel(new BorderLayout(0, 4));
    JLabel rightTitle = new JLabel("Data");
    rightTitle.setFont(ConsoleLook.uiBold());
    JPanel rightHeader = new JPanel(new BorderLayout(0, 4));
    rightHeader.setOpaque(false);
    rightHeader.add(rightTitle, BorderLayout.NORTH);
    rightHeader.add(previewBar, BorderLayout.SOUTH);
    right.add(rightHeader, BorderLayout.NORTH);
    right.add(rightScroll, BorderLayout.CENTER);

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
    split.setResizeWeight(0.28);
    split.setContinuousLayout(true);
    root.add(split, BorderLayout.CENTER);

    JPanel south = new JPanel(new BorderLayout(8, 8));
    log.setEditable(false);
    log.setLineWrap(true);
    log.setWrapStyleWord(true);
    log.setFont(ConsoleLook.uiSmall());
    JScrollPane logScroll = new JScrollPane(log);
    logScroll.setPreferredSize(new Dimension(10, 90));
    south.add(logScroll, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    refresh.addActionListener(e -> run("Refresh", this::refreshWork));
    initialize.addActionListener(e -> initializeClicked());
    firstLoginHelp.setToolTipText("Who can sign in to STS / Admin after Initialize?");
    firstLoginHelp.addActionListener(e -> showFirstLoginHelp(null));
    backup.addActionListener(e -> backupClicked());
    restore.addActionListener(e -> restoreClicked());
    JButton close = new JButton("Close");
    close.addActionListener(e -> dispose());
    buttons.add(refresh);
    buttons.add(initialize);
    buttons.add(firstLoginHelp);
    buttons.add(backup);
    buttons.add(restore);
    buttons.add(close);
    south.add(buttons, BorderLayout.SOUTH);
    root.add(south, BorderLayout.SOUTH);

    setContentPane(root);
    setMinimumSize(new Dimension(1020, 680));
    applyActions();
    pack();
    setLocationRelativeTo(owner);
    run("Refresh", this::refreshWork);
  }

  static void open(Frame owner, Path installHome, Supplier<LocalConfigFile.Form> settings) {
    open(owner, installHome, "", settings);
  }

  static void open(
      Frame owner, Path installHome, String connectionName, Supplier<LocalConfigFile.Form> settings) {
    new AdminDbDialog(owner, installHome, connectionName, settings).setVisible(true);
  }

  private static String titleFor(String connectionName, LocalConfigFile.Form form) {
    String name =
        connectionName == null || connectionName.isBlank() ? "identity database" : connectionName;
    String provider =
        form == null || form.provider == null || form.provider.isBlank()
            ? "?"
            : form.provider.trim().toLowerCase();
    return "Manage — " + name + " (" + provider + ")";
  }

  private void applyChrome(LocalConfigFile.Form form) {
    String provider = providerOf(form);
    String url = form == null || form.url == null ? "" : form.url.trim();
    String user =
        form == null || form.username == null || form.username.isBlank() ? "—" : form.username;
    currentBanner.setText(
        (connectionName.isBlank() ? "Current store" : connectionName)
            + "  ·  "
            + provider
            + "  ·  "
            + user);
    currentBanner.setToolTipText(url);
    StringBuilder helpText = new StringBuilder();
    helpText
        .append("Explore + emergency ops — not a substitute for Admin.\n")
        .append(
            "Filter tables · click column headers to sort · secrets masked · Edit row / double-click for emergency edit.\n")
        .append(
            "Restore auto-backs up first. Day-to-day Users/Clients changes belong in skoruba4j-admin.");
    if ("sqlite".equalsIgnoreCase(provider)) {
      helpText.append('\n').append(SqliteSchema.demoLoginHint()).append("  (First login…)");
    }
    help.setText(helpText.toString());
  }

  private int pageSize() {
    return ((Number) limitSpinner.getValue()).intValue();
  }

  private void pageBy(int delta) {
    int next = Math.max(0, ((Number) offsetSpinner.getValue()).intValue() + delta);
    offsetSpinner.setValue(next);
    loadPreview(selectedTable);
  }

  private void onTableSelected() {
    TableItem item = tableList.getSelectedValue();
    if (item == null) {
      selectedTable = "";
      clearData("Select a table");
      return;
    }
    if (!item.present()) {
      selectedTable = item.name();
      clearData("Table not present in this database.");
      return;
    }
    if (!item.name().equalsIgnoreCase(selectedTable)) {
      syncingPreviewControls = true;
      try {
        offsetSpinner.setValue(0);
        sortAscending = true;
        sortColumn = "";
      } finally {
        syncingPreviewControls = false;
      }
    }
    selectedTable = item.name();
    loadPreview(selectedTable);
  }

  private void editSelectedRow() {
    int row = dataTable.getSelectedRow();
    if (row < 0 || selectedTable == null || selectedTable.isBlank()) {
      JOptionPane.showMessageDialog(this, "Select a data row first.");
      return;
    }
    if (busy || loadingPreview) {
      return;
    }
    Map<String, String> cells = new LinkedHashMap<>();
    for (int c = 0; c < dataModel.getColumnCount(); c++) {
      Object name = dataModel.getColumnName(c);
      Object value = dataModel.getValueAt(row, c);
      cells.put(name == null ? "" : String.valueOf(name), value == null ? "" : String.valueOf(value));
    }
    String table = selectedTable;
    loadingPreview = true;
    applyActions();
    previewStatus.setText("Loading row…");
    new SwingWorker<IdentityStoreRowEditor.RowModel, Void>() {
      @Override
      protected IdentityStoreRowEditor.RowModel doInBackground() throws Exception {
        LocalConfigFile.Form form = settings.get();
        DataSource ds = JdbcClientChoices.open(form, installHome);
        DbProvider provider = DbProvider.fromConfig(form.provider);
        TableStyle style = TableStyle.fromConfig(form.tableStyle);
        SqlDialect dialect = new SqlDialect(provider);
        try (var connection = ds.getConnection()) {
          List<IdentityStoreRowEditor.ColumnInfo> columns =
              IdentityStoreRowEditor.describe(connection, dialect, table);
          Map<String, String> pk = new LinkedHashMap<>();
          for (IdentityStoreRowEditor.ColumnInfo column : columns) {
            if (!column.primaryKey()) {
              continue;
            }
            String value = cells.get(column.name());
            if (value == null) {
              for (Map.Entry<String, String> e : cells.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(column.name())) {
                  value = e.getValue();
                  break;
                }
              }
            }
            if (value == null || value.isBlank() || "••••••••".equals(value)) {
              throw new IllegalArgumentException(
                  "Cannot edit: primary key \"" + column.name() + "\" is missing in the preview.");
            }
            pk.put(column.name(), value);
          }
          if (pk.isEmpty()) {
            throw new IllegalArgumentException("This table has no usable primary key for editing.");
          }
          return IdentityStoreRowEditor.load(ds, provider, style, table, pk);
        }
      }

      @Override
      protected void done() {
        loadingPreview = false;
        applyActions();
        try {
          IdentityStoreRowEditor.RowModel model = get();
          previewStatus.setText("Editing…");
          // Own this Manage dialog, not the main Frame — nested modal otherwise freezes.
          RowEditDialog.Result result = RowEditDialog.open(AdminDbDialog.this, model);
          if (result == null) {
            previewStatus.setText("Edit cancelled");
            return;
          }
          saveRowEdit(model, result);
        } catch (Exception e) {
          previewStatus.setText(formatError(e));
          appendLog(formatError(e));
          JOptionPane.showMessageDialog(
              AdminDbDialog.this, formatError(e), "Edit row", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private void saveRowEdit(IdentityStoreRowEditor.RowModel model, RowEditDialog.Result result) {
    setBusy(true);
    appendLog("Update " + model.table() + "…");
    new SwingWorker<Integer, Void>() {
      @Override
      protected Integer doInBackground() throws Exception {
        LocalConfigFile.Form form = settings.get();
        DataSource ds = JdbcClientChoices.open(form, installHome);
        DbProvider provider = DbProvider.fromConfig(form.provider);
        TableStyle style = TableStyle.fromConfig(form.tableStyle);
        return IdentityStoreRowEditor.update(
            ds, provider, style, model, result.proposed(), result.newPassword());
      }

      @Override
      protected void done() {
        try {
          int n = get();
          appendLog(n == 0 ? "No columns updated." : "Updated " + n + " column(s).");
          previewStatus.setText(n == 0 ? "No changes applied" : "Saved " + n + " column(s)");
        } catch (Exception e) {
          appendLog(formatError(e));
          JOptionPane.showMessageDialog(
              AdminDbDialog.this, formatError(e), "Save failed", JOptionPane.ERROR_MESSAGE);
        } finally {
          setBusy(false);
          loadPreview(selectedTable);
        }
      }
    }.execute();
  }

  private void loadPreview(String table) {
    if (table == null || table.isBlank() || loadingPreview) {
      return;
    }
    int limit = pageSize();
    int offset = ((Number) offsetSpinner.getValue()).intValue();
    String order =
        sortColumn != null && !sortColumn.isBlank()
            ? sortColumn
            : (String) orderBy.getSelectedItem();
    boolean ascending = sortAscending;
    boolean reveal = revealSecrets.isSelected();
    loadingPreview = true;
    previewStatus.setText("Loading…");
    applyActions();
    new SwingWorker<IdentityStoreAdmin.Preview, Void>() {
      @Override
      protected IdentityStoreAdmin.Preview doInBackground() throws Exception {
        LocalConfigFile.Form form = settings.get();
        DataSource ds = JdbcClientChoices.open(form, installHome);
        DbProvider provider = DbProvider.fromConfig(form.provider);
        TableStyle style = TableStyle.fromConfig(form.tableStyle);
        return IdentityStoreAdmin.preview(
            ds, provider, style, table, offset, limit, order, ascending, reveal);
      }

      @Override
      protected void done() {
        loadingPreview = false;
        applyActions();
        try {
          showPreview(get());
        } catch (Exception e) {
          clearData(formatError(e));
          appendLog(formatError(e));
        }
      }
    }.execute();
  }

  private void showPreview(IdentityStoreAdmin.Preview preview) {
    dataModel.setRowCount(0);
    dataModel.setColumnCount(0);
    if (preview == null) {
      previewStatus.setText("No data");
      return;
    }
    if (preview.error() != null && !preview.error().isBlank()) {
      previewStatus.setText(preview.error());
      appendLog(preview.table() + ": " + preview.error());
      return;
    }
    syncingPreviewControls = true;
    try {
      DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
      for (String column : preview.columns()) {
        model.addElement(column);
      }
      orderBy.setModel(model);
      if (preview.orderBy() != null) {
        orderBy.setSelectedItem(preview.orderBy());
        sortColumn = preview.orderBy();
      }
      sortAscending = preview.ascending();
    } finally {
      syncingPreviewControls = false;
    }
    for (String column : preview.columns()) {
      dataModel.addColumn(column);
    }
    for (List<String> row : preview.rows()) {
      dataModel.addRow(row.toArray());
    }
    for (int i = 0; i < dataTable.getColumnCount(); i++) {
      dataTable.getColumnModel().getColumn(i).setPreferredWidth(120);
    }
    dataTable.getTableHeader().repaint();
    previewStatus.setText(
        "Showing "
            + preview.rows().size()
            + " row"
            + (preview.rows().size() == 1 ? "" : "s")
            + " · offset "
            + preview.offset()
            + " · order "
            + preview.orderBy()
            + (preview.ascending() ? " ASC" : " DESC")
            + (preview.secretsRevealed() ? " · secrets visible" : " · secrets masked"));
  }

  private void clearData(String status) {
    dataModel.setRowCount(0);
    dataModel.setColumnCount(0);
    previewStatus.setText(status == null ? "" : status);
  }

  private void showTables(IdentityStoreAdmin.Report report) {
    lastTables = report == null || report.tables() == null ? List.of() : report.tables();
    summary.setText(report == null ? "" : report.summary());
    rebuildTableList(selectedTable);
  }

  private void rebuildTableList(String keep) {
    String filter =
        tableFilter.getText() == null ? "" : tableFilter.getText().trim().toLowerCase(Locale.ROOT);
    String previous =
        tableList.getSelectedValue() == null ? keep : tableList.getSelectedValue().name();
    tableModel.clear();
    int select = -1;
    for (IdentityStoreAdmin.TableStatus row : lastTables) {
      if (!filter.isBlank() && !row.name().toLowerCase(Locale.ROOT).contains(filter)) {
        continue;
      }
      tableModel.addElement(TableItem.from(row));
      if (previous != null && previous.equalsIgnoreCase(row.name())) {
        select = tableModel.size() - 1;
      }
    }
    if (select >= 0) {
      tableList.setSelectedIndex(select);
    } else if (!tableModel.isEmpty() && (previous == null || previous.isBlank())) {
      tableList.setSelectedIndex(0);
    } else if (tableModel.isEmpty()) {
      clearData(filter.isBlank() ? "No tables" : "No tables match filter");
    }
  }

  private void initializeClicked() {
    LocalConfigFile.Form form = settings.get();
    if (!isSqlite(form)) {
      JOptionPane.showMessageDialog(
          this,
          "Initialize only creates the bundled SQLite schema.\n"
              + "This connection is "
              + providerOf(form)
              + ".");
      return;
    }
    int ok =
        JOptionPane.showConfirmDialog(
            this,
            "Create missing SQLite tables. If Users is empty, seed "
                + SqliteSchema.DEMO_USERNAME
                + " / "
                + SqliteSchema.DEMO_PASSWORD
                + " with role "
                + SqliteSchema.DEMO_ROLE
                + ".",
            "Initialize SQLite",
            JOptionPane.OK_CANCEL_OPTION);
    if (ok != JOptionPane.OK_OPTION) {
      return;
    }
    showLoginHelpAfterInit = true;
    run("Initialize", this::initializeWork);
  }

  private void showFirstLoginHelp(IdentityStoreAdmin.Report afterInit) {
    StringBuilder body = new StringBuilder();
    if (afterInit != null) {
      if (afterInit.demoSeeded()) {
        body.append("Initialize finished — DEMO user was seeded.\n\n");
      } else if (afterInit.ping()) {
        body.append(
            "Initialize finished — Users already had rows, so DEMO was not seeded.\n"
                + "Use an existing user that has idserver.admin.role.\n\n");
      }
    }
    body.append(SqliteSchema.demoLoginGuide());
    JOptionPane.showMessageDialog(
        this, body.toString(), "First login — STS / Admin", JOptionPane.INFORMATION_MESSAGE);
  }

  private void backupClicked() {
    JFileChooser chooser = sqliteChooser();
    chooser.setSelectedFile(new File(defaultBackupName()));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File file = chooser.getSelectedFile();
    if (file == null) {
      return;
    }
    Path path = ensureSqliteSuffix(file.toPath());
    if (Files.exists(path)) {
      int ok =
          JOptionPane.showConfirmDialog(
              this, "Overwrite " + path + " ?", "Backup", JOptionPane.OK_CANCEL_OPTION);
      if (ok != JOptionPane.OK_OPTION) {
        return;
      }
    }
    pendingBackup = path;
    run("Backup", this::backupWork);
  }

  private void restoreClicked() {
    JFileChooser chooser = sqliteChooser();
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File file = chooser.getSelectedFile();
    if (file == null) {
      return;
    }
    Path path = file.toPath();
    if (!Files.isRegularFile(path)) {
      JOptionPane.showMessageDialog(this, "Backup file not found: " + path);
      return;
    }
    LocalConfigFile.Form form = settings.get();
    String target =
        (connectionName.isBlank() ? providerOf(form) : connectionName)
            + "\n"
            + (form == null || form.url == null ? "" : form.url.trim());
    int ok =
        JOptionPane.showConfirmDialog(
            this,
            "Restore from:\n"
                + path.toAbsolutePath()
                + "\n\ninto this database:\n"
                + target
                + "\n\nA safety backup of the current database is written first under data/.\n"
                + "Existing identity rows in this database are then deleted and replaced.",
            "Restore",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (ok != JOptionPane.OK_OPTION) {
      return;
    }
    pendingRestore = path;
    run("Restore", this::restoreWork);
  }

  private String defaultBackupName() {
    String stem =
        connectionName.isBlank()
            ? "skoruba4j-backup"
            : connectionName.replaceAll("[^a-zA-Z0-9._-]+", "_");
    return stem + "-" + LocalDateTime.now().format(BACKUP_STAMP) + ".sqlite";
  }

  private static Path ensureSqliteSuffix(Path path) {
    if (path.getFileName().toString().contains(".")) {
      return path;
    }
    return path.resolveSibling(path.getFileName() + ".sqlite");
  }

  private JFileChooser sqliteChooser() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("SQLite (*.sqlite, *.db)", "sqlite", "db"));
    Path data = installHome == null ? null : installHome.resolve("data");
    if (data != null && Files.isDirectory(data)) {
      chooser.setCurrentDirectory(data.toFile());
    }
    return chooser;
  }

  private IdentityStoreAdmin.Report refreshWork() throws Exception {
    LocalConfigFile.Form form = settings.get();
    DataSource ds = JdbcClientChoices.open(form, installHome);
    DbProvider provider = DbProvider.fromConfig(form.provider);
    TableStyle style = TableStyle.fromConfig(form.tableStyle);
    return IdentityStoreAdmin.inspect(ds, provider, style);
  }

  private IdentityStoreAdmin.Report initializeWork() throws Exception {
    LocalConfigFile.Form form = settings.get();
    DataSource ds = JdbcClientChoices.open(form, installHome);
    TableStyle style = TableStyle.fromConfig(form.tableStyle);
    return IdentityStoreAdmin.initializeSqlite(ds, style, form.url, installHome);
  }

  private IdentityStoreAdmin.Report backupWork() throws Exception {
    LocalConfigFile.Form form = settings.get();
    DataSource source = JdbcClientChoices.open(form, installHome);
    TableStyle style = TableStyle.fromConfig(form.tableStyle);
    Path file = pendingBackup;
    Map<String, Integer> copied = IdentityStoreCopy.backupToFile(source, file, style);
    extraLog = formatCounts("Backup written to " + file.toAbsolutePath(), copied);
    return IdentityStoreAdmin.inspect(source, DbProvider.fromConfig(form.provider), style);
  }

  private IdentityStoreAdmin.Report restoreWork() throws Exception {
    LocalConfigFile.Form live = settings.get();
    TableStyle style = TableStyle.fromConfig(live.tableStyle);
    DbProvider destProvider = DbProvider.fromConfig(live.provider);
    DataSource dest = JdbcClientChoices.open(live, installHome);
    Path safety = safetyBackupPath();
    Files.createDirectories(safety.getParent());
    Map<String, Integer> safetyCopied = IdentityStoreCopy.backupToFile(dest, safety, style);
    extraLog =
        formatCounts("Safety backup before restore → " + safety.toAbsolutePath(), safetyCopied)
            + "\n";
    Map<String, Integer> copied =
        IdentityStoreCopy.restoreFromFile(pendingRestore, dest, destProvider, style);
    extraLog += formatCounts("Restored into " + destProvider.name().toLowerCase(Locale.ROOT), copied);
    return IdentityStoreAdmin.inspect(dest, destProvider, style);
  }

  private Path safetyBackupPath() {
    Path data = installHome == null ? Path.of("data") : installHome.resolve("data");
    String stem =
        connectionName.isBlank()
            ? "pre-restore"
            : "pre-restore-" + connectionName.replaceAll("[^a-zA-Z0-9._-]+", "_");
    return data.resolve(stem + "-" + LocalDateTime.now().format(BACKUP_STAMP) + ".sqlite");
  }

  private static String formatCounts(String title, Map<String, Integer> copied) {
    StringBuilder extra = new StringBuilder(title).append('\n');
    int tables = 0;
    int rows = 0;
    for (Map.Entry<String, Integer> entry : copied.entrySet()) {
      int n = entry.getValue() == null ? 0 : entry.getValue();
      extra.append(entry.getKey()).append(": ").append(n < 0 ? "skipped" : n).append('\n');
      if (n >= 0) {
        tables++;
        rows += n;
      }
    }
    extra.append(tables).append(" tables, ").append(rows).append(" rows.");
    return extra.toString();
  }

  private void run(String label, Work work) {
    extraLog = "";
    setBusy(true);
    appendLog(label + "…");
    new SwingWorker<IdentityStoreAdmin.Report, Void>() {
      @Override
      protected IdentityStoreAdmin.Report doInBackground() {
        try {
          return work.run();
        } catch (Exception e) {
          String failure = formatError(e);
          extraLog = failure;
          return new IdentityStoreAdmin.Report(false, failure, List.of());
        }
      }

      @Override
      protected void done() {
        try {
          if (extraLog != null && !extraLog.isBlank()) {
            appendLog(extraLog);
            extraLog = "";
          }
          IdentityStoreAdmin.Report report = get();
          showTables(report);
          appendLog(report == null ? "" : report.summary());
          if (showLoginHelpAfterInit) {
            showLoginHelpAfterInit = false;
            if (report != null && report.ping()) {
              showFirstLoginHelp(report);
            }
          }
        } catch (Exception e) {
          showLoginHelpAfterInit = false;
          summary.setText(e.getMessage());
          appendLog(formatError(e));
        } finally {
          setBusy(false);
        }
      }
    }.execute();
  }

  private static String formatError(Throwable error) {
    Throwable t = error;
    while (t.getCause() != null
        && (t instanceof java.util.concurrent.ExecutionException
            || t.getMessage() == null
            || t.getMessage().isBlank())) {
      t = t.getCause();
    }
    String msg = t.getMessage();
    return msg == null || msg.isBlank() ? t.toString() : msg;
  }

  private void appendLog(String text) {
    if (text == null || text.isBlank()) {
      return;
    }
    if (log.getText() == null || log.getText().isBlank()) {
      log.setText(text);
    } else {
      log.append("\n" + text);
    }
    log.setCaretPosition(log.getDocument().getLength());
  }

  private void setBusy(boolean value) {
    busy = value;
    applyActions();
  }

  private void applyActions() {
    boolean sqlite = isSqlite(settings.get());
    boolean idle = !busy && !loadingPreview;
    refresh.setEnabled(!busy);
    initialize.setEnabled(!busy && sqlite);
    initialize.setVisible(sqlite);
    firstLoginHelp.setEnabled(!busy);
    firstLoginHelp.setVisible(sqlite);
    backup.setEnabled(!busy);
    restore.setEnabled(!busy);
    reloadRows.setEnabled(idle);
    editRow.setEnabled(idle);
    prevPage.setEnabled(idle);
    nextPage.setEnabled(idle);
    tableList.setEnabled(!busy);
    dataTable.setEnabled(!busy);
    tableFilter.setEnabled(!busy);
    limitSpinner.setEnabled(idle);
    offsetSpinner.setEnabled(idle);
    orderBy.setEnabled(idle);
    revealSecrets.setEnabled(idle);
  }

  private static boolean isSqlite(LocalConfigFile.Form form) {
    return form != null && "sqlite".equalsIgnoreCase(form.provider);
  }

  private static String providerOf(LocalConfigFile.Form form) {
    if (form == null || form.provider == null || form.provider.isBlank()) {
      return "?";
    }
    return form.provider.trim().toLowerCase(Locale.ROOT);
  }

  private record TableItem(String name, boolean present, int rows) {
    static TableItem from(IdentityStoreAdmin.TableStatus status) {
      return new TableItem(status.name(), status.present(), status.rows());
    }

    @Override
    public String toString() {
      if (!present) {
        return name + "  —";
      }
      if (rows < 0) {
        return name;
      }
      return name + "  (" + rows + ")";
    }
  }

  @FunctionalInterface
  private interface Work {
    IdentityStoreAdmin.Report run() throws Exception;
  }
}
