package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.console.configfile.JdbcConnectionStore;
import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import com.myano.skoruba4j.console.jdbc.JdbcClientChoices;
import com.myano.skoruba4j.console.jdbc.JdbcUrlComposer;
import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Modal editor for a named JDBC connection profile. */
final class ConnectionEditorDialog extends JDialog {
  private final Path repoRoot;
  private final JdbcConnectionStore.Store store;
  private final String exceptId;
  private final String previousPassword;

  private final JTextField connectionName = new JTextField();
  private final JComboBox<String> provider = new JComboBox<>(LocalConfigFile.PROVIDERS);
  private final JLabel driverLabel = new JLabel(" ");
  private final JTextField host = new JTextField();
  private final JTextField port = new JTextField();
  private final JTextField databaseName = new JTextField();
  private final JTextField sqliteFile = new JTextField();
  private final JTextField extra = new JTextField();
  private final JTextArea urlPreview = new JTextArea(3, 40);
  private final JCheckBox editUrl = new JCheckBox("Edit JDBC URL manually (advanced)");
  private final CompactCards connectionCards = new CompactCards();
  private final JTextField username = new JTextField();
  private final JPasswordField password = new JPasswordField();
  private final JCheckBox showPassword = new JCheckBox("Show DB password");
  private final JTextField tableStyle = new JTextField();
  private final JLabel testStatus = new JLabel(" ");
  private final char hiddenEcho;
  private boolean syncingJdbc;
  private JdbcConnectionStore.Connection result;

  private ConnectionEditorDialog(
      Frame owner,
      Path repoRoot,
      JdbcConnectionStore.Store store,
      JdbcConnectionStore.Connection seed,
      String title) {
    super(owner, title, true);
    this.repoRoot = repoRoot;
    this.store = store;
    this.exceptId = seed == null ? null : seed.id;
    this.previousPassword = seed == null || seed.password == null ? "" : seed.password;
    this.hiddenEcho = password.getEchoChar();
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(720, 560));
    getContentPane().setLayout(new BorderLayout());
    getContentPane().setBackground(ConsoleLook.PAPER);
    getContentPane().add(new JScrollPane(buildForm()), BorderLayout.CENTER);
    getContentPane().add(buttonBar(), BorderLayout.SOUTH);
    loadSeed(seed);
    pack();
    setLocationRelativeTo(owner);
  }

  static JdbcConnectionStore.Connection create(
      Frame owner, Path repoRoot, JdbcConnectionStore.Store store) {
    JdbcConnectionStore.Connection seed = new JdbcConnectionStore.Connection();
    seed.id = JdbcConnectionStore.newId();
    seed.name = JdbcConnectionStore.uniqueName(store, "New connection");
    seed.provider = "sqlite";
    seed.url = LocalConfigFile.EMBEDDED_JDBC_URL;
    seed.tableStyle = LocalConfigFile.DEFAULT_TABLE_STYLE;
    return open(owner, repoRoot, store, seed, "New database connection");
  }

  static JdbcConnectionStore.Connection duplicate(
      Frame owner,
      Path repoRoot,
      JdbcConnectionStore.Store store,
      JdbcConnectionStore.Connection source) {
    if (source == null) {
      return null;
    }
    JdbcConnectionStore.Connection seed = source.copy();
    seed.id = JdbcConnectionStore.newId();
    seed.name = JdbcConnectionStore.uniqueName(store, source.name + " copy");
    return open(owner, repoRoot, store, seed, "Duplicate database connection");
  }

  static JdbcConnectionStore.Connection edit(
      Frame owner,
      Path repoRoot,
      JdbcConnectionStore.Store store,
      JdbcConnectionStore.Connection existing) {
    if (existing == null) {
      return null;
    }
    return open(owner, repoRoot, store, existing.copy(), "Edit database connection");
  }

  private static JdbcConnectionStore.Connection open(
      Frame owner,
      Path repoRoot,
      JdbcConnectionStore.Store store,
      JdbcConnectionStore.Connection seed,
      String title) {
    ConnectionEditorDialog dialog = new ConnectionEditorDialog(owner, repoRoot, store, seed, title);
    dialog.setVisible(true);
    return dialog.result;
  }

  private JPanel buildForm() {
    JPanel form = ConsoleLook.form();
    form.setBackground(ConsoleLook.PAPER);
    form.setOpaque(true);
    form.setBorder(new EmptyBorder(12, 16, 8, 16));
    GridBagConstraints gc = ConsoleLook.formGc();

    ConsoleLook.addSection(form, gc, "Connection");
    ConsoleLook.addRow(form, gc, "Name", connectionName);
    ConsoleLook.addRow(form, gc, "DB type", provider);
    provider.setEditable(false);
    provider.addActionListener(e -> onProviderChanged());
    driverLabel.setFont(ConsoleLook.uiSmall());
    driverLabel.setForeground(ConsoleLook.MUTED);
    ConsoleLook.addRow(form, gc, "Driver", driverLabel);

    ConsoleLook.addSection(form, gc, "Location");
    connectionCards.setOpaque(false);
    connectionCards.add(sqlitePanel(), "sqlite");
    connectionCards.add(serverPanel(), "server");
    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    form.add(connectionCards, gc);
    gc.gridwidth = 1;

    ConsoleLook.addSection(form, gc, "Credentials");
    ConsoleLook.addRow(form, gc, "Username", username);
    ConsoleLook.addRow(form, gc, "Password", password);
    gc.gridy++;
    gc.gridx = 1;
    showPassword.setFont(ConsoleLook.uiSmall());
    showPassword.addActionListener(
        e -> password.setEchoChar(showPassword.isSelected() ? 0 : hiddenEcho));
    form.add(showPassword, gc);
    ConsoleLook.addRow(form, gc, "Table style", tableStyle);

    ConsoleLook.addSection(form, gc, "JDBC URL");
    urlPreview.setRows(2);
    urlPreview.setLineWrap(true);
    urlPreview.setWrapStyleWord(true);
    urlPreview.setEditable(false);
    urlPreview.setFont(ConsoleLook.mono().deriveFont(11f));
    urlPreview.setBackground(ConsoleLook.CARD);
    urlPreview.setForeground(ConsoleLook.INK);
    urlPreview.setBorder(
        javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(ConsoleLook.LINE),
            new EmptyBorder(4, 6, 4, 6)));
    JScrollPane urlScroll = new JScrollPane(urlPreview);
    urlScroll.setBorder(null);
    urlScroll.setPreferredSize(new Dimension(10, 48));
    ConsoleLook.addRow(form, gc, "Preview", urlScroll);
    gc.gridy++;
    gc.gridx = 1;
    editUrl.setFont(ConsoleLook.uiSmall());
    editUrl.addActionListener(e -> toggleUrlEdit());
    form.add(editUrl, gc);

    DocumentListener recompose = simpleListener(this::recomposeUrlFromFields);
    host.getDocument().addDocumentListener(recompose);
    port.getDocument().addDocumentListener(recompose);
    databaseName.getDocument().addDocumentListener(recompose);
    sqliteFile.getDocument().addDocumentListener(recompose);
    extra.getDocument().addDocumentListener(recompose);

    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.insets = new Insets(6, 0, 0, 0);
    testStatus.setFont(ConsoleLook.uiSmall());
    testStatus.setForeground(ConsoleLook.MUTED);
    form.add(testStatus, gc);
    return form;
  }

  private JPanel buttonBar() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    bar.setOpaque(false);
    bar.setBorder(new EmptyBorder(8, 16, 12, 16));
    JButton test = ConsoleLook.outlineButton("Test connection", ConsoleLook.WINE, ConsoleLook.WINE);
    test.addActionListener(e -> testConnection());
    JButton cancel = ConsoleLook.outlineButton("Cancel", ConsoleLook.LINE, ConsoleLook.INK);
    cancel.addActionListener(
        e -> {
          result = null;
          dispose();
        });
    JButton ok = ConsoleLook.primary("Save");
    ok.addActionListener(e -> accept());
    bar.add(test);
    bar.add(cancel);
    bar.add(ok);
    return bar;
  }

  private JPanel sqlitePanel() {
    JPanel form = ConsoleLook.form();
    form.setBorder(new EmptyBorder(0, 0, 0, 0));
    GridBagConstraints gc = ConsoleLook.formGc();
    gc.gridy++;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    form.add(ConsoleLook.fieldLabel("DB file"), gc);
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JPanel row = new JPanel(new BorderLayout(6, 0));
    row.setOpaque(false);
    row.add(sqliteFile, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.setOpaque(false);
    JButton browse = ConsoleLook.primary("Browse…");
    browse.setToolTipText("Open an existing file, or Create empty… inside the file dialog.");
    browse.addActionListener(e -> browseSqliteFile());
    JButton install = ConsoleLook.outlineButton("Default file", ConsoleLook.WINE, ConsoleLook.WINE);
    install.setToolTipText("First-install SQLite file: " + JdbcUrlComposer.portableSqliteFile());
    install.addActionListener(e -> useDefaultSqlite());
    buttons.add(browse);
    buttons.add(install);
    row.add(buttons, BorderLayout.EAST);
    form.add(row, gc);
    ConsoleLook.addRow(form, gc, "Extra (query)", extra);
    return form;
  }

  private JPanel serverPanel() {
    JPanel form = ConsoleLook.form();
    form.setBorder(new EmptyBorder(0, 0, 0, 0));
    GridBagConstraints gc = ConsoleLook.formGc();
    ConsoleLook.addRow(form, gc, "Host / IP", host);
    ConsoleLook.addRow(form, gc, "Port", port);
    ConsoleLook.addRow(form, gc, "Database name", databaseName);
    ConsoleLook.addRow(form, gc, "Extra properties", extra);
    return form;
  }

  private void loadSeed(JdbcConnectionStore.Connection seed) {
    if (seed == null) {
      return;
    }
    syncingJdbc = true;
    try {
      connectionName.setText(seed.name == null ? "" : seed.name);
      LocalConfigFile.Form form = new LocalConfigFile.Form();
      JdbcConnectionStore.applyToForm(seed, form);
      applyForm(form);
    } finally {
      syncingJdbc = false;
    }
  }

  private void applyForm(LocalConfigFile.Form form) {
    String prov = LocalConfigFile.canonicalizeProvider(form.provider);
    provider.setSelectedItem(prov);
    driverLabel.setText(JdbcUrlComposer.driverClass(prov));
    JdbcUrlComposer.Parts parts = JdbcUrlComposer.parse(prov, form.url, repoRoot);
    if (JdbcUrlComposer.isStaleExtra(prov, parts.extra)) {
      parts.extra = JdbcUrlComposer.defaultExtra(prov);
    }
    // Dirty sqlserver URLs from older builds: never stay in raw/manual mode.
    if ("sqlserver".equals(prov)
        && parts.rawOnly
        && form.url != null
        && (form.url.contains("journal_mode=") || form.url.contains("&"))) {
      parts = JdbcUrlComposer.parse(prov, JdbcUrlComposer.normalize(prov, form.url, repoRoot), repoRoot);
    }
    editUrl.setSelected(parts.rawOnly);
    urlPreview.setEditable(parts.rawOnly);
    host.setText(parts.host == null ? "" : parts.host);
    port.setText(parts.port == null ? "" : parts.port);
    databaseName.setText(parts.database == null ? "" : parts.database);
    sqliteFile.setText(parts.sqliteFile == null ? "" : parts.sqliteFile);
    extra.setText(parts.extra == null ? "" : parts.extra);
    if (parts.rawOnly) {
      urlPreview.setText(JdbcUrlComposer.normalize(prov, parts.rawUrl, repoRoot));
    } else {
      urlPreview.setText(JdbcUrlComposer.compose(parts, repoRoot));
    }
    connectionCards.show("sqlite".equals(prov) ? "sqlite" : "server");
    username.setText(form.username);
    password.setText(form.password);
    tableStyle.setText(form.tableStyle);
  }

  private void accept() {
    String name = connectionName.getText() == null ? "" : connectionName.getText().trim();
    if (name.isBlank()) {
      JOptionPane.showMessageDialog(this, "Connection name is required.");
      return;
    }
    if (JdbcConnectionStore.nameTaken(store, name, exceptId)) {
      JOptionPane.showMessageDialog(this, "Connection name already exists.");
      return;
    }
    LocalConfigFile.Form form = editorForm();
    JdbcConnectionStore.Connection c = new JdbcConnectionStore.Connection();
    c.id = exceptId == null || exceptId.isBlank() ? JdbcConnectionStore.newId() : exceptId;
    c.name = name;
    c.provider = form.provider;
    c.url = form.url;
    c.username = form.username;
    String pwd = form.password;
    c.password = (pwd == null || pwd.isBlank()) ? previousPassword : pwd;
    c.tableStyle = form.tableStyle;
    try {
      if ("sqlite".equalsIgnoreCase(c.provider)) {
        ensureSqliteParent(c.url, repoRoot);
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
      return;
    }
    result = c;
    dispose();
  }

  private LocalConfigFile.Form editorForm() {
    LocalConfigFile.Form form = new LocalConfigFile.Form();
    form.provider = (String) provider.getSelectedItem();
    form.url = currentJdbcUrl();
    form.username = username.getText();
    form.password = new String(password.getPassword());
    form.tableStyle = tableStyle.getText();
    return form;
  }

  private void testConnection() {
    LocalConfigFile.Form snapshot = editorForm();
    testStatus.setForeground(ConsoleLook.MUTED);
    testStatus.setText("Testing…");
    new SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() throws Exception {
        if ("sqlite".equalsIgnoreCase(snapshot.provider)) {
          ensureSqliteParent(snapshot.url, repoRoot);
        }
        DataSource ds = JdbcClientChoices.open(snapshot, repoRoot);
        try (Connection connection = ds.getConnection();
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("SELECT 1")) {
          rs.next();
        }
        return "OK — connected with " + DbProvider.fromConfig(snapshot.provider).driverClassName();
      }

      @Override
      protected void done() {
        try {
          testStatus.setForeground(ConsoleLook.UP);
          testStatus.setText(get());
        } catch (Exception e) {
          String detail = formatJdbcError(e);
          testStatus.setForeground(ConsoleLook.DOWN);
          testStatus.setText("Failed — " + detail);
          JOptionPane.showMessageDialog(
              ConnectionEditorDialog.this,
              detail,
              "Test connection",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private static String formatJdbcError(Throwable error) {
    Throwable t = error;
    while (t.getCause() != null
        && (t instanceof java.util.concurrent.ExecutionException
            || t instanceof java.lang.reflect.InvocationTargetException
            || t.getMessage() == null
            || t.getMessage().isBlank())) {
      t = t.getCause();
    }
    String msg = t.getMessage();
    if (msg != null && msg.contains("/") && !msg.contains(" ")) {
      // NoClassDefFoundError / ClassNotFoundException often use binary names.
      return t.getClass().getSimpleName()
          + ": "
          + msg.replace('/', '.')
          + "\nRestart Skoruba4j Control after rebuilding so JDBC drivers are on the classpath.";
    }
    if (msg == null || msg.isBlank()) {
      return t.toString();
    }
    return msg;
  }

  private void useDefaultSqlite() {
    syncingJdbc = true;
    try {
      provider.setSelectedItem("sqlite");
      editUrl.setSelected(false);
      urlPreview.setEditable(false);
      sqliteFile.setText(JdbcUrlComposer.portableSqliteFile());
      extra.setText(JdbcUrlComposer.SQLITE_DEFAULT_EXTRA);
      username.setText("");
      password.setText("");
      connectionCards.show("sqlite");
      driverLabel.setText(JdbcUrlComposer.driverClass("sqlite"));
    } finally {
      syncingJdbc = false;
    }
    recomposeUrlFromFields();
    if (tableStyle.getText().isBlank()) {
      tableStyle.setText(LocalConfigFile.DEFAULT_TABLE_STYLE);
    }
  }

  private void onProviderChanged() {
    if (syncingJdbc) {
      return;
    }
    String prov = (String) provider.getSelectedItem();
    driverLabel.setText(JdbcUrlComposer.driverClass(prov));
    connectionCards.show("sqlite".equalsIgnoreCase(prov) ? "sqlite" : "server");
    if (!editUrl.isSelected()) {
      if ("sqlite".equalsIgnoreCase(prov)) {
        if (sqliteFile.getText().isBlank()) {
          sqliteFile.setText(JdbcUrlComposer.portableSqliteFile());
        }
        if (JdbcUrlComposer.isStaleExtra(prov, extra.getText())) {
          extra.setText(JdbcUrlComposer.SQLITE_DEFAULT_EXTRA);
        }
      } else {
        if (host.getText().isBlank()) {
          host.setText("localhost");
        }
        int defPort = JdbcUrlComposer.defaultPort(prov);
        if (port.getText().isBlank() || "0".equals(port.getText().trim())) {
          port.setText(Integer.toString(defPort));
        } else if (defPort > 0) {
          // Switching sqlite→server or between engines: refresh common default ports.
          String current = port.getText().trim();
          if ("1433".equals(current) || "5432".equals(current) || "3306".equals(current)) {
            port.setText(Integer.toString(defPort));
          }
        }
        if (JdbcUrlComposer.isStaleExtra(prov, extra.getText())) {
          extra.setText(JdbcUrlComposer.defaultExtra(prov));
        }
      }
      recomposeUrlFromFields();
    }
  }

  private void recomposeUrlFromFields() {
    if (syncingJdbc || editUrl.isSelected()) {
      return;
    }
    syncingJdbc = true;
    try {
      urlPreview.setText(JdbcUrlComposer.compose(readParts(), repoRoot));
    } finally {
      syncingJdbc = false;
    }
  }

  private void toggleUrlEdit() {
    boolean manual = editUrl.isSelected();
    urlPreview.setEditable(manual);
    if (!manual) {
      recomposeUrlFromFields();
    }
  }

  private void browseSqliteFile() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Choose SQLite file");
    chooser.setFileFilter(new FileNameExtensionFilter("SQLite (*.sqlite, *.db)", "sqlite", "db"));
    prepareSqliteChooser(chooser);
    chooser.setAccessory(sqliteChooserCreateAccessory(chooser));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
        && chooser.getSelectedFile() != null) {
      applySqlitePath(chooser.getSelectedFile().toPath());
    }
  }

  private JPanel sqliteChooserCreateAccessory(JFileChooser chooser) {
    JPanel accessory = new JPanel(new BorderLayout(0, 8));
    accessory.setBorder(new EmptyBorder(8, 10, 8, 10));
    accessory.setOpaque(false);
    JLabel hint =
        new JLabel(
            "<html><body style='width:120px'>Select an existing file,<br/>"
                + "or create a new empty<br/>database below.</body></html>");
    hint.setFont(ConsoleLook.uiSmall());
    hint.setForeground(ConsoleLook.MUTED);
    JButton create = ConsoleLook.primary("Create empty…");
    create.setToolTipText(
        "Choose a directory and file name, create an empty .sqlite (no tables), then return here.");
    create.addActionListener(
        e -> {
          Path created =
              CreateEmptySqliteDialog.show(
                  this,
                  chooser.getCurrentDirectory() == null
                      ? null
                      : chooser.getCurrentDirectory().toPath(),
                  repoRoot);
          if (created != null) {
            applySqlitePath(created);
            testStatus.setForeground(ConsoleLook.UP);
            testStatus.setText("Created empty SQLite file — schema via Manage → Initialize");
            chooser.setSelectedFile(created.toFile());
            chooser.approveSelection();
          }
        });
    accessory.add(hint, BorderLayout.NORTH);
    accessory.add(create, BorderLayout.SOUTH);
    return accessory;
  }

  private void prepareSqliteChooser(JFileChooser chooser) {
    Path start = repoRoot == null ? null : repoRoot.resolve("data");
    String current = sqliteFile.getText().trim();
    if (!current.isBlank() && !current.contains("${")) {
      File file = new File(current);
      if (file.getParentFile() != null && file.getParentFile().isDirectory()) {
        chooser.setCurrentDirectory(file.getParentFile());
      }
    } else if (start != null && Files.isDirectory(start)) {
      chooser.setCurrentDirectory(start.toFile());
    } else if (repoRoot != null) {
      chooser.setCurrentDirectory(repoRoot.toFile());
    }
  }

  private void applySqlitePath(Path chosenRaw) {
    Path chosen = SqlitePaths.ensureSqliteSuffix(chosenRaw.toAbsolutePath().normalize());
    Path defaultFile = SqlitePaths.file(repoRoot);
    if (defaultFile != null && chosen.equals(defaultFile.toAbsolutePath().normalize())) {
      sqliteFile.setText(JdbcUrlComposer.portableSqliteFile());
    } else {
      sqliteFile.setText(chosen.toString().replace('\\', '/'));
    }
    recomposeUrlFromFields();
  }

  private String currentJdbcUrl() {
    if (editUrl.isSelected()) {
      return urlPreview.getText() == null ? "" : urlPreview.getText().trim();
    }
    return JdbcUrlComposer.compose(readParts(), repoRoot);
  }

  private JdbcUrlComposer.Parts readParts() {
    JdbcUrlComposer.Parts parts = new JdbcUrlComposer.Parts();
    parts.provider = (String) provider.getSelectedItem();
    parts.host = host.getText();
    parts.port = port.getText();
    parts.database = databaseName.getText();
    parts.sqliteFile = sqliteFile.getText();
    parts.extra = extra.getText();
    parts.rawOnly = editUrl.isSelected();
    parts.rawUrl = urlPreview.getText();
    return parts;
  }

  private static void ensureSqliteParent(String jdbcUrl, Path installHome) throws java.io.IOException {
    if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
      return;
    }
    String rest = jdbcUrl.substring("jdbc:sqlite:".length());
    int query = rest.indexOf('?');
    if (query >= 0) {
      rest = rest.substring(0, query);
    }
    Path home = installHome == null ? Path.of(".") : installHome.toAbsolutePath().normalize();
    String homeAbs = home.toString().replace('\\', '/');
    rest =
        rest.replace("${IDSERVER_HOME}", homeAbs)
            .replace("${idserver.home}", homeAbs)
            .replace('\\', '/');
    Path db = Path.of(rest);
    if (!db.isAbsolute()) {
      db = home.resolve(rest);
    }
    if (db.getParent() != null) {
      Files.createDirectories(db.getParent());
    }
  }

  private static DocumentListener simpleListener(Runnable action) {
    return new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        action.run();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        action.run();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        action.run();
      }
    };
  }

  private static final class CompactCards extends JPanel {
    private final CardLayout cards = new CardLayout();

    CompactCards() {
      super();
      setLayout(cards);
      setOpaque(false);
    }

    void show(String name) {
      cards.show(this, name == null ? "sqlite" : name);
      revalidate();
      if (getParent() != null) {
        getParent().revalidate();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      for (java.awt.Component child : getComponents()) {
        if (child.isVisible()) {
          return child.getPreferredSize();
        }
      }
      return super.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      Dimension pref = getPreferredSize();
      return new Dimension(Integer.MAX_VALUE, pref.height);
    }
  }
}
