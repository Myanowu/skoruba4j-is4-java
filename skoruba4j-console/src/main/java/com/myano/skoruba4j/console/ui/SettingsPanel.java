package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.console.configfile.JdbcConnectionStore;
import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import com.myano.skoruba4j.console.jdbc.JdbcClientChoices;
import com.myano.skoruba4j.i18n.Messages;
import com.myano.skoruba4j.i18n.UiLocale;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

final class SettingsPanel extends JPanel {
  private final Path repoRoot;
  private final Path file;
  private final Path connectionsFile;
  private final Consumer<UiLocale> onLocale;
  private JdbcConnectionStore.Store connStore = new JdbcConnectionStore.Store();
  private String selectedId = "";
  private boolean syncingConnList;
  private boolean syncingLang;
  private final List<String> connectionRowIds = new ArrayList<>();
  private final DefaultTableModel connectionsModel =
      new DefaultTableModel(
          new Object[] {
            Messages.t("control.db.col.name"),
            Messages.t("control.db.col.type"),
            Messages.t("control.db.col.url"),
            Messages.t("control.db.col.user"),
            Messages.t("control.db.col.status"),
            ""
          },
          0) {
        @Override
        public boolean isCellEditable(int row, int column) {
          return column == 5;
        }
      };
  private final JTable connectionsTable = new JTable(connectionsModel);
  private final JTabbedPane tabs = new JTabbedPane();
  private final JButton saveButton = ConsoleLook.primary(Messages.t("control.save"));
  private final JComboBox<UiLocale> languageCombo = new JComboBox<>();
  private final JLabel languageStatus = new JLabel(" ");
  private final JLabel languageNote = new JLabel();
  private final JButton languageApply = ConsoleLook.primary(Messages.t("control.lang.apply"));
  private final JLabel dbStatus = new JLabel(" ");
  private JButton neuButton;
  private JButton editButton;
  private JButton dupButton;
  private JButton delButton;
  private JButton activateButton;
  private final JTextField issuerUri = new JTextField();
  private final JComboBox<String> endSession = new JComboBox<>(LocalConfigFile.END_SESSION_MODES);
  /** Maps to idserver.admin.login-mode (and oidc-enabled for sts-oidc only). */
  private final JComboBox<String> adminSignIn =
      new JComboBox<>(
          new String[] {
            "Local password",
            "STS password (Admin page, no redirect)",
            "STS OIDC redirect (SSO)"
          });
  private final JComboBox<JdbcClientChoices.Item> clientId = new JComboBox<>();
  private final JPasswordField clientSecret = new JPasswordField();
  private final JLabel clientIdStatus = new JLabel(" ");
  private final JComboBox<String> adminRole = new JComboBox<>();
  private final JLabel adminRoleStatus = new JLabel(" ");
  private final JTextField keyStore = new JTextField();
  private final JPasswordField keyStorePassword = new JPasswordField();
  private final JComboBox<String> keyStoreType = new JComboBox<>(LocalConfigFile.STORE_TYPES);
  private final JTextField keyAlias = new JTextField();
  private final JTextField trustStore = new JTextField();
  private final JPasswordField trustStorePassword = new JPasswordField();
  private final JComboBox<String> trustStoreType = new JComboBox<>(LocalConfigFile.STORE_TYPES);
  private final JCheckBox showTlsPassword = new JCheckBox("Show certificate passwords");
  private final JTextField smtpHost = new JTextField();
  private final JTextField smtpPort = new JTextField();
  private final JTextField smtpUsername = new JTextField();
  private final JPasswordField smtpPassword = new JPasswordField();
  private final JTextField smtpFrom = new JTextField();
  private final JCheckBox smtpStartTls = new JCheckBox("SMTP STARTTLS");
  private final JPasswordField smtpResetKey = new JPasswordField();
  private final JCheckBox showSmtpPassword = new JCheckBox("Show SMTP password / reset-key");
  private final ProcessFields stsFields = new ProcessFields();
  private final ProcessFields adminFields = new ProcessFields();
  private final ProcessFields apiFields = new ProcessFields();
  private final JComboBox<String> adminApiUi = new JComboBox<>(new String[] {"Yes", "No"});
  private final JComboBox<String> adminApiLoginMode =
      new JComboBox<>(
          new String[] {
            "Both (local + STS OIDC)",
            "Local password only",
            "STS OIDC only (Google / Microsoft / WhatsApp / WeChat)"
          });
  private final JTextField adminApiClientId = new JTextField();
  private final JPasswordField adminApiClientSecret = new JPasswordField();
  private final JTextField googleClientId = new JTextField();
  private final JPasswordField googleClientSecret = new JPasswordField();
  private final JTextField microsoftClientId = new JTextField();
  private final JPasswordField microsoftClientSecret = new JPasswordField();
  private final JTextField microsoftTenantId = new JTextField();
  private final JTextField whatsappBusinessPhone = new JTextField();
  private final JPasswordField whatsappWebhookVerifyToken = new JPasswordField();
  private final JPasswordField whatsappAppSecret = new JPasswordField();
  private final JTextField wechatAppId = new JTextField();
  private final JPasswordField wechatAppSecret = new JPasswordField();
  private final JCheckBox showExternalSecrets = new JCheckBox("Show IdP secrets");
  private final char hiddenEcho;

  SettingsPanel(Path repoRoot, Consumer<UiLocale> onLocale) {
    super(new BorderLayout(0, 0));
    this.repoRoot = repoRoot;
    this.onLocale = onLocale == null ? locale -> {} : onLocale;
    this.file = LocalConfigFile.resolve(repoRoot);
    this.connectionsFile = JdbcConnectionStore.resolve(repoRoot);
    this.hiddenEcho = keyStorePassword.getEchoChar();
    setBorder(new EmptyBorder(12, 16, 8, 16));
    setBackground(ConsoleLook.PAPER);
    tabs.addTab(Messages.t("control.tab.database"), scroll(databasePanel()));
    tabs.addTab(Messages.t("control.tab.tls"), scroll(tlsPanel()));
    tabs.addTab(Messages.t("control.tab.sts"), scroll(stsPanel()));
    tabs.addTab(Messages.t("control.tab.external"), scroll(externalPanel()));
    tabs.addTab(Messages.t("control.tab.admin"), scroll(adminPanel()));
    tabs.addTab(Messages.t("control.tab.adminApi"), scroll(apiPanel()));
    tabs.addTab(Messages.t("control.tab.language"), scroll(languagePanel()));
    ConsoleLook.styleTabbedPane(tabs);
    add(tabs, BorderLayout.CENTER);
    saveButton.setToolTipText(
        "Saves TLS / STS / Admin / Admin API. Database profiles use New / Edit / Set as current.");
    saveButton.addActionListener(e -> save());
    JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    south.setOpaque(false);
    south.setBorder(new EmptyBorder(6, 0, 0, 0));
    south.add(saveButton);
    add(south, BorderLayout.SOUTH);
    reload();
  }

  /** Refresh Settings chrome after the Control language changes. */
  void applyLocale() {
    tabs.setTitleAt(0, Messages.t("control.tab.database"));
    tabs.setTitleAt(1, Messages.t("control.tab.tls"));
    tabs.setTitleAt(2, Messages.t("control.tab.sts"));
    tabs.setTitleAt(3, Messages.t("control.tab.external"));
    tabs.setTitleAt(4, Messages.t("control.tab.admin"));
    tabs.setTitleAt(5, Messages.t("control.tab.adminApi"));
    tabs.setTitleAt(6, Messages.t("control.tab.language"));
    saveButton.setText(Messages.t("control.save"));
    connectionsModel.setColumnIdentifiers(
        new Object[] {
          Messages.t("control.db.col.name"),
          Messages.t("control.db.col.type"),
          Messages.t("control.db.col.url"),
          Messages.t("control.db.col.user"),
          Messages.t("control.db.col.status"),
          ""
        });
    if (neuButton != null) {
      neuButton.setText(Messages.t("control.db.new"));
      editButton.setText(Messages.t("control.db.edit"));
      dupButton.setText(Messages.t("control.db.duplicate"));
      delButton.setText(Messages.t("control.db.delete"));
      activateButton.setText(Messages.t("control.db.setCurrent"));
    }
    languageApply.setText(Messages.t("control.lang.apply"));
    languageNote.setText(
        "<html><body style='width:620px;font-family:\"Microsoft JhengHei UI\",\"Microsoft YaHei UI\",SansSerif;font-size:11px;color:#6B5558'>"
            + Messages.t("control.lang.note")
            + "</body></html>");
    syncingLang = true;
    try {
      languageCombo.setSelectedItem(UiLocale.current());
    } finally {
      syncingLang = false;
    }
    languageStatus.setText("");
    refreshConnectionList(selectedId);
  }

  private JPanel languagePanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    ConsoleLook.addSection(form, gc, Messages.t("control.lang.title"));
    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    languageNote.setText(
        "<html><body style='width:620px;font-family:\"Microsoft JhengHei UI\",\"Microsoft YaHei UI\",SansSerif;font-size:11px;color:#6B5558'>"
            + Messages.t("control.lang.note")
            + "</body></html>");
    languageNote.setFont(ConsoleLook.uiSmall());
    languageNote.setForeground(ConsoleLook.MUTED);
    form.add(languageNote, gc);
    gc.gridwidth = 1;
    DefaultComboBoxModel<UiLocale> model = new DefaultComboBoxModel<>();
    for (UiLocale locale : UiLocale.values()) {
      model.addElement(locale);
    }
    languageCombo.setModel(model);
    languageCombo.setSelectedItem(UiLocale.current());
    languageCombo.setRenderer(
        new javax.swing.DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
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
    addRow(form, gc, Messages.t("lang.choose"), languageCombo);
    gc.gridy++;
    gc.gridx = 1;
    languageApply.addActionListener(
        e -> {
          if (syncingLang) {
            return;
          }
          if (languageCombo.getSelectedItem() instanceof UiLocale locale) {
            onLocale.accept(locale);
            languageStatus.setText(Messages.t("control.lang.saved"));
            languageStatus.setForeground(ConsoleLook.UP);
          }
        });
    form.add(languageApply, gc);
    gc.gridy++;
    gc.gridx = 1;
    languageStatus.setFont(ConsoleLook.uiSmall());
    languageStatus.setForeground(ConsoleLook.MUTED);
    form.add(languageStatus, gc);
    return form;
  }

  private JScrollPane scroll(JPanel form) {
    JScrollPane scroll = new JScrollPane(form);
    scroll.setBorder(null);
    scroll.getViewport().setBackground(ConsoleLook.PAPER);
    scroll.setBackground(ConsoleLook.PAPER);
    return scroll;
  }

  private JPanel databasePanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();

    ConsoleLook.addSection(form, gc, Messages.t("control.db.connections"));
    ConsoleLook.addNote(form, gc, Messages.t("control.db.note"));

    connectionsTable.setFont(ConsoleLook.ui());
    connectionsTable.setRowHeight(34);
    connectionsTable.setFillsViewportHeight(true);
    connectionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    connectionsTable.setShowHorizontalLines(true);
    connectionsTable.setShowVerticalLines(false);
    connectionsTable.setGridColor(ConsoleLook.LINE);
    connectionsTable.getTableHeader().setFont(ConsoleLook.uiBold());
    connectionsTable.getTableHeader().setReorderingAllowed(false);
    connectionsTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    connectionsTable.getColumnModel().getColumn(0).setPreferredWidth(140);
    connectionsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
    connectionsTable.getColumnModel().getColumn(2).setPreferredWidth(320);
    connectionsTable.getColumnModel().getColumn(3).setPreferredWidth(110);
    connectionsTable.getColumnModel().getColumn(4).setPreferredWidth(70);
    connectionsTable.getColumnModel().getColumn(5).setPreferredWidth(88);
    connectionsTable.getColumnModel().getColumn(5).setMaxWidth(100);
    connectionsTable.getColumnModel().getColumn(5).setCellRenderer(new ManageButtonRenderer());
    connectionsTable.getColumnModel().getColumn(5).setCellEditor(new ManageButtonEditor());
    connectionsTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting() && !syncingConnList) {
                syncSelectedIdFromTable();
              }
            });
    DefaultTableCellRenderer wrap =
        new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(
              JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c =
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (c instanceof JLabel label) {
              label.setToolTipText(value == null ? null : String.valueOf(value));
              if (column == 4
                  && value != null
                  && Messages.t("control.db.current").equals(String.valueOf(value))) {
                label.setForeground(ConsoleLook.UP);
                label.setFont(ConsoleLook.uiBold());
              } else {
                label.setForeground(isSelected ? label.getForeground() : ConsoleLook.INK);
                label.setFont(ConsoleLook.ui());
              }
            }
            return c;
          }
        };
    for (int i = 0; i < 5; i++) {
      connectionsTable.getColumnModel().getColumn(i).setCellRenderer(wrap);
    }

    JScrollPane tableScroll = new JScrollPane(connectionsTable);
    tableScroll.setBorder(javax.swing.BorderFactory.createLineBorder(ConsoleLook.LINE));
    tableScroll.setPreferredSize(new java.awt.Dimension(10, 280));
    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    form.add(tableScroll, gc);
    gc.weighty = 0;
    gc.gridwidth = 1;

    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.insets = new Insets(8, 0, 2, 0);
    JPanel listActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    listActions.setOpaque(false);
    neuButton = ConsoleLook.primary(Messages.t("control.db.new"));
    editButton = ConsoleLook.outlineButton(Messages.t("control.db.edit"), ConsoleLook.WINE, ConsoleLook.WINE);
    dupButton =
        ConsoleLook.outlineButton(Messages.t("control.db.duplicate"), ConsoleLook.LINE, ConsoleLook.INK);
    delButton =
        ConsoleLook.outlineButton(Messages.t("control.db.delete"), ConsoleLook.LINE, ConsoleLook.INK);
    activateButton = ConsoleLook.primary(Messages.t("control.db.setCurrent"));
    neuButton.addActionListener(e -> newConnection());
    editButton.addActionListener(e -> editConnection());
    dupButton.addActionListener(e -> duplicateConnection());
    delButton.addActionListener(e -> deleteConnection());
    activateButton.addActionListener(e -> setAsCurrent());
    listActions.add(neuButton);
    listActions.add(editButton);
    listActions.add(dupButton);
    listActions.add(delButton);
    listActions.add(activateButton);
    form.add(listActions, gc);

    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.insets = new Insets(0, 0, 0, 0);
    dbStatus.setFont(ConsoleLook.uiSmall());
    dbStatus.setForeground(ConsoleLook.MUTED);
    form.add(dbStatus, gc);
    gc.gridwidth = 1;
    return form;
  }

  private JPanel tlsPanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    addNote(form, gc, "HTTPS certificate used by any process whose protocol is HTTPS.");
    addBrowseRow(form, gc, "HTTPS key store (p12/jks)", keyStore);
    addRow(form, gc, "Key store password", keyStorePassword);
    addRow(form, gc, "Key store type", keyStoreType);
    keyStoreType.setEditable(false);
    addRow(form, gc, "Key alias", keyAlias);
    addBrowseRow(form, gc, "Admin outbound trust store (blank = Windows ROOT + HTTPS key store)", trustStore);
    addRow(form, gc, "Trust store password", trustStorePassword);
    addRow(form, gc, "Trust store type", trustStoreType);
    trustStoreType.setEditable(false);
    gc.gridy++;
    gc.gridx = 1;
    showTlsPassword.addActionListener(
        e -> {
          char echo = showTlsPassword.isSelected() ? 0 : hiddenEcho;
          keyStorePassword.setEchoChar(echo);
          trustStorePassword.setEchoChar(echo);
          clientSecret.setEchoChar(echo);
        });
    form.add(showTlsPassword, gc);
    return form;
  }

  private JPanel stsPanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    addNote(
        form,
        gc,
        "skoruba4j-sts: login and /connect/* tokens. Listen protocol and port should match Admin issuer-uri.");
    addProcessRows(form, gc, stsFields);
    endSession.setEditable(false);
    addRow(form, gc, "logout.end-session", endSession);
    addNote(
        form,
        gc,
        "compatible = IdentityServer4 refresh and lenient endsession. strict = expire JWT immediately, no refresh.");
    addNote(
        form,
        gc,
        "Forgot-password mail. Leave host blank to skip sending. Password and reset-key stay the previous value if you leave them empty on Save.");
    addRow(form, gc, "SMTP host", smtpHost);
    addRow(form, gc, "SMTP port", smtpPort);
    addRow(form, gc, "SMTP username", smtpUsername);
    addRow(form, gc, "SMTP password", smtpPassword);
    addRow(form, gc, "From address", smtpFrom);
    gc.gridy++;
    gc.gridx = 1;
    form.add(smtpStartTls, gc);
    addRow(form, gc, "Reset-token key", smtpResetKey);
    gc.gridy++;
    gc.gridx = 1;
    showSmtpPassword.addActionListener(
        e -> {
          char echo = showSmtpPassword.isSelected() ? 0 : hiddenEcho;
          smtpPassword.setEchoChar(echo);
          smtpResetKey.setEchoChar(echo);
        });
    form.add(showSmtpPassword, gc);
    return form;
  }

  private JPanel adminPanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    addNote(
        form,
        gc,
        "skoruba4j-admin: management UI. OIDC client_id and role come from the database.");
    addProcessRows(form, gc, adminFields);
    addRow(form, gc, "issuer-uri", issuerUri);
    addNote(
        form,
        gc,
        "OIDC Authority (token iss). Example https://localhost:5051 — must match the STS listen URL.");
    adminSignIn.setEditable(false);
    addRow(form, gc, "Admin sign-in", adminSignIn);
    addNote(
        form,
        gc,
        "One mode only. Local = Users table. STS password = Admin form calls STS password grant (no browser redirect; not cross-app SSO). STS OIDC = browser SSO. Client must allow password grant for STS password. Restart Admin after Save.");
    adminSignIn.addActionListener(e -> syncAdminSignInFields());
    clientId.setEditable(false);
    addClientIdRow(form, gc);
    addRow(form, gc, "Admin client-secret", clientSecret);
    addNote(
        form,
        gc,
        "Plaintext secret for STS token calls (password grant or OIDC) when the Client requires a secret. Leave blank on Save to keep the previous value.");
    adminRole.setEditable(false);
    addAdminRoleRow(form, gc);
    return form;
  }

  private JPanel apiPanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    addNote(
        form,
        gc,
        "skoruba4j-admin-api: REST over the same tables. JWT role must match Admin role.");
    addProcessRows(form, gc, apiFields);
    adminApiUi.setEditable(false);
    addRow(form, gc, "Enable browser UI", adminApiUi);
    addNote(
        form,
        gc,
        "Yes (default): browser console at / and /ui/**. No: JWT /api/** and /health only — UI paths are denied. Restart Admin API after Save.");
    adminApiLoginMode.setEditable(false);
    addRow(form, gc, "UI sign-in", adminApiLoginMode);
    addNote(
        form,
        gc,
        "STS OIDC uses client skoruba4j-admin-api. WhatsApp / WeChat QR appear after External IdP is filled and STS restarted. Per-client flags: Admin → Clients.");
    addRow(form, gc, "API OIDC client-id", adminApiClientId);
    addRow(form, gc, "API OIDC client-secret", adminApiClientSecret);
    addNote(
        form,
        gc,
        "Leave secret blank for public PKCE client (recommended). Must match Clients.ClientId redirects to this Admin API host /signin-oidc.");
    return form;
  }

  private JPanel externalPanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    addNote(
        form,
        gc,
        "STS global IdP credentials (idserver.external-login). Leave a secret blank on Save to keep the previous value. Restart STS (and Admin API for login buttons) after Save. Per-client enable is in Admin → Clients.");
    ConsoleLook.addSection(form, gc, "Google");
    addRow(form, gc, "Client ID", googleClientId);
    addRow(form, gc, "Client secret", googleClientSecret);
    ConsoleLook.addSection(form, gc, "Microsoft");
    addRow(form, gc, "Client ID", microsoftClientId);
    addRow(form, gc, "Client secret", microsoftClientSecret);
    addRow(form, gc, "Tenant ID", microsoftTenantId);
    ConsoleLook.addSection(form, gc, "WhatsApp QR (Cloud API)");
    addRow(form, gc, "Business phone (wa.me digits)", whatsappBusinessPhone);
    addNote(
        form,
        gc,
        "Required to show WhatsApp QR. Example 85291234567. Meta webhook: {issuer}/external/whatsapp/webhook");
    addRow(form, gc, "Webhook verify token", whatsappWebhookVerifyToken);
    addRow(form, gc, "App secret (optional)", whatsappAppSecret);
    ConsoleLook.addSection(form, gc, "WeChat QR (Open Platform 网站应用)");
    addRow(form, gc, "App ID", wechatAppId);
    addRow(form, gc, "App secret", wechatAppSecret);
    addNote(
        form,
        gc,
        "Callback URL in WeChat console: {issuer}/external/wechat/callback");
    showExternalSecrets.setOpaque(false);
    showExternalSecrets.addActionListener(
        e -> {
          char echo = showExternalSecrets.isSelected() ? (char) 0 : hiddenEcho;
          googleClientSecret.setEchoChar(echo);
          microsoftClientSecret.setEchoChar(echo);
          whatsappWebhookVerifyToken.setEchoChar(echo);
          whatsappAppSecret.setEchoChar(echo);
          wechatAppSecret.setEchoChar(echo);
        });
    addRow(form, gc, "", showExternalSecrets);
    return form;
  }

  void reload() {
    try {
      String overlay = LocalConfigFile.read(file);
      String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
      LocalConfigFile.Form form = LocalConfigFile.load(overlay, privateYaml);
      loadConnectionStore();
      issuerUri.setText(form.issuerUri);
      endSession.setSelectedItem(LocalConfigFile.canonicalizeEndSession(form.endSession));
      adminSignIn.setSelectedItem(labelForLoginMode(form.loginMode));
      clientSecret.setText(form.clientSecret);
      keyStore.setText(form.keyStore);
      keyStorePassword.setText(form.keyStorePassword);
      keyStoreType.setSelectedItem(form.keyStoreType);
      keyAlias.setText(form.keyAlias);
      trustStore.setText(form.trustStore);
      trustStorePassword.setText(form.trustStorePassword);
      trustStoreType.setSelectedItem(form.trustStoreType);
      fillProcess(stsFields, form.sts);
      fillProcess(adminFields, form.adminProc);
      fillProcess(apiFields, form.adminApi);
      adminApiUi.setSelectedItem(form.adminApiUiEnabled ? "Yes" : "No");
      adminApiLoginMode.setSelectedItem(labelForApiLoginMode(form.adminApiLoginMode));
      adminApiClientId.setText(form.adminApiClientId);
      adminApiClientSecret.setText(form.adminApiClientSecret);
      smtpHost.setText(form.smtpHost);
      smtpPort.setText(Integer.toString(form.smtpPort <= 0 ? 587 : form.smtpPort));
      smtpUsername.setText(form.smtpUsername);
      smtpPassword.setText(form.smtpPassword);
      smtpFrom.setText(form.smtpFrom);
      smtpStartTls.setSelected(form.smtpStartTls);
      smtpResetKey.setText(form.smtpResetKey);
      googleClientId.setText(form.googleClientId);
      googleClientSecret.setText(form.googleClientSecret);
      microsoftClientId.setText(form.microsoftClientId);
      microsoftClientSecret.setText(form.microsoftClientSecret);
      microsoftTenantId.setText(form.microsoftTenantId);
      whatsappBusinessPhone.setText(form.whatsappBusinessPhone);
      whatsappWebhookVerifyToken.setText(form.whatsappWebhookVerifyToken);
      whatsappAppSecret.setText(form.whatsappAppSecret);
      wechatAppId.setText(form.wechatAppId);
      wechatAppSecret.setText(form.wechatAppSecret);
      loadCatalogFromDb(form.clientId, form.adminRole);
      syncAdminSignInFields();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
    }
  }

  private String selectedLoginMode() {
    return loginModeForLabel((String) adminSignIn.getSelectedItem());
  }

  private boolean usesStsClient() {
    String mode = selectedLoginMode();
    return "sts-password".equals(mode) || "sts-oidc".equals(mode);
  }

  private static String labelForLoginMode(String mode) {
    return switch (LocalConfigFile.canonicalizeLoginMode(mode)) {
      case "local" -> "Local password";
      case "sts-oidc" -> "STS OIDC redirect (SSO)";
      default -> "STS password (Admin page, no redirect)";
    };
  }

  private static String loginModeForLabel(String label) {
    if ("Local password".equals(label)) {
      return "local";
    }
    if ("STS OIDC redirect (SSO)".equals(label)) {
      return "sts-oidc";
    }
    return "sts-password";
  }

  private static String labelForApiLoginMode(String mode) {
    return switch (LocalConfigFile.canonicalizeApiLoginMode(mode)) {
      case "local" -> "Local password only";
      case "sts-oidc" -> "STS OIDC only (Google / Microsoft / WhatsApp / WeChat)";
      default -> "Both (local + STS OIDC)";
    };
  }

  private static String apiLoginModeForLabel(String label) {
    if ("Local password only".equals(label)) {
      return "local";
    }
    if (label != null && label.startsWith("STS OIDC only")) {
      return "sts-oidc";
    }
    return "both";
  }

  private void syncAdminSignInFields() {
    boolean sts = usesStsClient();
    clientId.setEnabled(sts);
    clientSecret.setEnabled(sts);
  }

  private void save() {
    try {
      String previous = LocalConfigFile.read(file);
      String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
      LocalConfigFile.Form previousForm = LocalConfigFile.load(previous, privateYaml);
      LocalConfigFile.Form form = new LocalConfigFile.Form();
      // Database profiles are saved via Save connection / Set as current — keep projected db.
      form.provider = previousForm.provider;
      form.url = previousForm.url;
      form.username = previousForm.username;
      form.password = previousForm.password;
      form.tableStyle = previousForm.tableStyle;
      form.issuerUri = issuerUri.getText();
      form.endSession = (String) endSession.getSelectedItem();
      form.loginMode = selectedLoginMode();
      form.oidcEnabled = "sts-oidc".equals(form.loginMode);
      JdbcClientChoices.Item selected = (JdbcClientChoices.Item) clientId.getSelectedItem();
      if (usesStsClient()
          && (selected == null || selected.clientId() == null || selected.clientId().isBlank())) {
        JOptionPane.showMessageDialog(
            this, "Load Clients from the database and select Admin client-id.");
        return;
      }
      form.clientId = selected == null ? "" : selected.clientId();
      form.clientSecret = new String(clientSecret.getPassword());
      String role = (String) adminRole.getSelectedItem();
      if (usesStsClient() && (role == null || role.isBlank())) {
        JOptionPane.showMessageDialog(this, "Load Roles from the database and select Admin role.");
        return;
      }
      form.adminRole = role == null ? "" : role.trim();
      form.sslEnabled = readProcess(stsFields).sslEnabled;
      form.keyStore = keyStore.getText();
      form.keyStorePassword = new String(keyStorePassword.getPassword());
      form.keyStoreType = (String) keyStoreType.getSelectedItem();
      form.keyAlias = keyAlias.getText();
      form.trustStore = trustStore.getText();
      form.trustStorePassword = new String(trustStorePassword.getPassword());
      form.trustStoreType = (String) trustStoreType.getSelectedItem();
      form.sts = readProcess(stsFields);
      form.adminProc = readProcess(adminFields);
      form.adminApi = readProcess(apiFields);
      form.adminApiUiEnabled = !"No".equals(adminApiUi.getSelectedItem());
      form.adminApiLoginMode = apiLoginModeForLabel((String) adminApiLoginMode.getSelectedItem());
      form.adminApiClientId = adminApiClientId.getText();
      form.adminApiClientSecret = new String(adminApiClientSecret.getPassword());
      form.smtpHost = smtpHost.getText();
      form.smtpPort = LocalConfigFile.parseInt(smtpPort.getText(), 587);
      form.smtpUsername = smtpUsername.getText();
      form.smtpPassword = new String(smtpPassword.getPassword());
      form.smtpFrom = smtpFrom.getText();
      form.smtpStartTls = smtpStartTls.isSelected();
      form.smtpResetKey = new String(smtpResetKey.getPassword());
      form.googleClientId = googleClientId.getText();
      form.googleClientSecret = new String(googleClientSecret.getPassword());
      form.microsoftClientId = microsoftClientId.getText();
      form.microsoftClientSecret = new String(microsoftClientSecret.getPassword());
      form.microsoftTenantId = microsoftTenantId.getText();
      form.whatsappBusinessPhone = whatsappBusinessPhone.getText();
      form.whatsappWebhookVerifyToken = new String(whatsappWebhookVerifyToken.getPassword());
      form.whatsappAppSecret = new String(whatsappAppSecret.getPassword());
      form.wechatAppId = wechatAppId.getText();
      form.wechatAppSecret = new String(wechatAppSecret.getPassword());
      if ("sqlite".equalsIgnoreCase(form.provider)) {
        ensureSqliteParent(form.url, repoRoot);
      }
      LocalConfigFile.save(file, LocalConfigFile.write(form, previous));
      Path jvm = file.getParent() == null ? Path.of("jvm.env.cmd") : file.getParent().resolve("jvm.env.cmd");
      Files.writeString(jvm, LocalConfigFile.jvmEnvCmd(form), StandardCharsets.UTF_8);
      reload();
      JOptionPane.showMessageDialog(
          this, "Saved. Restart each process for listen port, heap, pool, and TLS to apply.");
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
    }
  }

  private void loadConnectionStore() {
    try {
      connStore = JdbcConnectionStore.loadOrSeed(repoRoot);
      if (!Files.exists(connectionsFile)) {
        JdbcConnectionStore.save(connectionsFile, connStore);
      }
    } catch (Exception e) {
      connStore = new JdbcConnectionStore.Store();
      JOptionPane.showMessageDialog(this, "Could not load connections: " + e.getMessage());
    }
    String select = connStore.activeId;
    if (select == null || select.isBlank()) {
      JdbcConnectionStore.Connection active = connStore.active();
      select = active == null ? "" : active.id;
    }
    refreshConnectionList(select);
  }

  private void refreshConnectionList(String selectId) {
    syncingConnList = true;
    try {
      if (connectionsTable.isEditing()) {
        connectionsTable.getCellEditor().stopCellEditing();
      }
      connectionsModel.setRowCount(0);
      connectionRowIds.clear();
      int selectRow = -1;
      for (JdbcConnectionStore.Connection c : connStore.connections) {
        boolean current = c.id != null && c.id.equals(connStore.activeId);
        String user = c.username == null || c.username.isBlank() ? "—" : c.username;
        connectionsModel.addRow(
            new Object[] {
              nullToEmpty(c.name),
              nullToEmpty(c.provider),
              nullToEmpty(c.url),
              user,
              current ? Messages.t("control.db.current") : "",
              Messages.t("control.db.manage")
            });
        connectionRowIds.add(c.id);
        if (selectId != null && selectId.equals(c.id)) {
          selectRow = connectionRowIds.size() - 1;
        }
      }
      if (selectRow < 0 && !connectionRowIds.isEmpty()) {
        selectRow = 0;
      }
      if (selectRow >= 0) {
        connectionsTable.setRowSelectionInterval(selectRow, selectRow);
        selectedId = connectionRowIds.get(selectRow);
      } else {
        selectedId = "";
      }
    } finally {
      syncingConnList = false;
    }
  }

  private void syncSelectedIdFromTable() {
    int row = connectionsTable.getSelectedRow();
    if (row < 0 || row >= connectionRowIds.size()) {
      selectedId = "";
      return;
    }
    selectedId = connectionRowIds.get(row);
  }

  private Frame ownerFrame() {
    return (Frame) javax.swing.SwingUtilities.getWindowAncestor(this);
  }

  private void newConnection() {
    JdbcConnectionStore.Connection created =
        ConnectionEditorDialog.create(ownerFrame(), repoRoot, connStore);
    if (created == null) {
      return;
    }
    upsertAndSave(created);
  }

  private void editConnection() {
    JdbcConnectionStore.Connection existing = connStore.find(selectedId);
    if (existing == null) {
      JOptionPane.showMessageDialog(this, "Select a connection first.");
      return;
    }
    JdbcConnectionStore.Connection edited =
        ConnectionEditorDialog.edit(ownerFrame(), repoRoot, connStore, existing);
    if (edited == null) {
      return;
    }
    upsertAndSave(edited);
  }

  private void duplicateConnection() {
    JdbcConnectionStore.Connection src = connStore.find(selectedId);
    if (src == null) {
      JOptionPane.showMessageDialog(this, "Select a connection first.");
      return;
    }
    JdbcConnectionStore.Connection created =
        ConnectionEditorDialog.duplicate(ownerFrame(), repoRoot, connStore, src);
    if (created == null) {
      return;
    }
    upsertAndSave(created);
  }

  private void upsertAndSave(JdbcConnectionStore.Connection c) {
    JdbcConnectionStore.Connection existing = connStore.find(c.id);
    if (existing == null) {
      connStore.connections.add(c);
    } else {
      existing.name = c.name;
      existing.provider = c.provider;
      existing.url = c.url;
      existing.username = c.username;
      existing.password = c.password;
      existing.tableStyle = c.tableStyle;
    }
    try {
      JdbcConnectionStore.save(connectionsFile, connStore);
      refreshConnectionList(c.id);
      dbStatus.setForeground(ConsoleLook.UP);
      dbStatus.setText("Connection saved.");
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
    }
  }

  private void deleteConnection() {
    if (connStore.connections.size() <= 1) {
      JOptionPane.showMessageDialog(this, "Keep at least one database connection.");
      return;
    }
    JdbcConnectionStore.Connection c = connStore.find(selectedId);
    if (c == null) {
      return;
    }
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            "Delete connection \"" + c.name + "\"?",
            "Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }
    boolean wasActive = c.id.equals(connStore.activeId);
    connStore.connections.removeIf(x -> c.id.equals(x.id));
    JdbcConnectionStore.ensureActive(connStore);
    try {
      JdbcConnectionStore.save(connectionsFile, connStore);
      if (wasActive) {
        projectActiveToLocalYaml();
      }
      refreshConnectionList(connStore.activeId);
      dbStatus.setForeground(ConsoleLook.MUTED);
      dbStatus.setText("Connection deleted.");
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
    }
  }

  private void setAsCurrent() {
    JdbcConnectionStore.Connection c = connStore.find(selectedId);
    if (c == null) {
      JOptionPane.showMessageDialog(this, "Select a connection first.");
      return;
    }
    connStore.activeId = c.id;
    try {
      JdbcConnectionStore.save(connectionsFile, connStore);
      projectActiveToLocalYaml();
      refreshConnectionList(c.id);
      loadCatalogFromDb(currentClientId(), currentAdminRole());
      dbStatus.setForeground(ConsoleLook.UP);
      dbStatus.setText("Set as current identity store. Restart STS / Admin / Admin API.");
      JOptionPane.showMessageDialog(
          this,
          "Current identity store updated.\nRestart STS, Admin, and Admin API for the change to apply.",
          "Set as current",
          JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
    }
  }

  private void projectActiveToLocalYaml() throws Exception {
    JdbcConnectionStore.Connection active = connStore.active();
    if (active == null) {
      return;
    }
    String previous = LocalConfigFile.read(file);
    String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
    LocalConfigFile.Form form = LocalConfigFile.load(previous, privateYaml);
    String keepPassword = form.password;
    JdbcConnectionStore.applyToForm(active, form);
    if (form.password == null || form.password.isBlank()) {
      form.password = keepPassword;
    }
    if ("sqlite".equalsIgnoreCase(form.provider)) {
      ensureSqliteParent(form.url, repoRoot);
    }
    LocalConfigFile.save(file, LocalConfigFile.write(form, previous));
    Path jvm =
        file.getParent() == null ? Path.of("jvm.env.cmd") : file.getParent().resolve("jvm.env.cmd");
    Files.writeString(jvm, LocalConfigFile.jvmEnvCmd(form), StandardCharsets.UTF_8);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private void openAdminDbForRow(int row) {
    if (row < 0 || row >= connectionRowIds.size()) {
      return;
    }
    openAdminDbFor(connStore.find(connectionRowIds.get(row)));
  }

  private void openAdminDbFor(JdbcConnectionStore.Connection connection) {
    if (connection == null) {
      JOptionPane.showMessageDialog(this, "Select a connection first.");
      return;
    }
    Frame owner = ownerFrame();
    String label =
        connection.name == null || connection.name.isBlank() ? connection.id : connection.name;
    AdminDbDialog.open(
        owner,
        repoRoot,
        label,
        () -> {
          LocalConfigFile.Form form = jdbcSnapshot();
          JdbcConnectionStore.applyToForm(connection, form);
          return form;
        });
    loadCatalogFromDb(currentClientId(), currentAdminRole());
  }

  private final class ManageButtonRenderer extends JButton implements TableCellRenderer {
    /** Renders the Manage action using the current Control locale. */
    ManageButtonRenderer() {
      setOpaque(true);
      setFont(ConsoleLook.uiSmall());
      setFocusPainted(false);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setText(value == null ? Messages.t("control.db.manage") : String.valueOf(value));
      setForeground(ConsoleLook.WINE);
      setBackground(isSelected ? ConsoleLook.CREAM : ConsoleLook.CARD);
      return this;
    }
  }

  private final class ManageButtonEditor extends AbstractCellEditor implements TableCellEditor {
    private final JButton button =
        ConsoleLook.outlineButton(Messages.t("control.db.manage"), ConsoleLook.WINE, ConsoleLook.WINE);
    private int editingRow = -1;

    ManageButtonEditor() {
      button.addActionListener(
          e -> {
            int row = editingRow;
            fireEditingStopped();
            openAdminDbForRow(row);
          });
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int row, int column) {
      editingRow = row;
      button.setText(value == null ? Messages.t("control.db.manage") : String.valueOf(value));
      return button;
    }

    @Override
    public Object getCellEditorValue() {
      return Messages.t("control.db.manage");
    }
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

  private void addBrowseRow(JPanel form, GridBagConstraints gc, String label, JTextField field) {
    gc.gridy++;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    form.add(ConsoleLook.fieldLabel(label), gc);
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    row.add(field, BorderLayout.CENTER);
    JButton browse = ConsoleLook.primary("Browse…");
    browse.addActionListener(e -> browseStore(field));
    row.add(browse, BorderLayout.EAST);
    form.add(row, gc);
  }

  private void browseStore(JTextField field) {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Key stores (p12, pfx, jks)", "p12", "pfx", "jks"));
    String current = LocalConfigFile.filesystemPath(field.getText(), repoRoot);
    if (!current.isBlank()) {
      File start = new File(current);
      if (start.getParentFile() != null && start.getParentFile().isDirectory()) {
        chooser.setCurrentDirectory(start.getParentFile());
      }
    } else if (repoRoot != null) {
      chooser.setCurrentDirectory(repoRoot.toFile());
    }
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      field.setText(LocalConfigFile.springFileLocation(chooser.getSelectedFile().toPath()));
    }
  }

  private void addClientIdRow(JPanel form, GridBagConstraints gc) {
    gc.gridy++;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    form.add(ConsoleLook.fieldLabel("Admin client-id"), gc);
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    row.add(clientId, BorderLayout.CENTER);
    JButton load = ConsoleLook.primary("Load from database");
    load.addActionListener(e -> loadCatalogFromDb(currentClientId(), currentAdminRole()));
    row.add(load, BorderLayout.EAST);
    form.add(row, gc);
    gc.gridy++;
    gc.gridx = 1;
    clientIdStatus.setFont(ConsoleLook.uiSmall());
    clientIdStatus.setForeground(ConsoleLook.MUTED);
    form.add(clientIdStatus, gc);
  }

  private void addAdminRoleRow(JPanel form, GridBagConstraints gc) {
    gc.gridy++;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    form.add(ConsoleLook.fieldLabel("Admin role"), gc);
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    form.add(adminRole, gc);
    gc.gridy++;
    gc.gridx = 1;
    adminRoleStatus.setFont(ConsoleLook.uiSmall());
    adminRoleStatus.setForeground(ConsoleLook.MUTED);
    form.add(adminRoleStatus, gc);
  }

  private void loadCatalogFromDb(String preferClient, String preferRole) {
    LocalConfigFile.Form snapshot = jdbcSnapshot();
    clientId.setEnabled(false);
    adminRole.setEnabled(false);
    clientIdStatus.setText("Loading Clients…");
    adminRoleStatus.setText("Loading Roles…");
    new SwingWorker<JdbcClientChoices.Result, Void>() {
      @Override
      protected JdbcClientChoices.Result doInBackground() {
        return JdbcClientChoices.load(snapshot, repoRoot);
      }

      @Override
      protected void done() {
        try {
          JdbcClientChoices.Result result = get();
          String clientError = result.error();
          String roleError = result.error();
          if (clientError.isBlank() && result.items().isEmpty()) {
            clientError = "No enabled Clients in this database.";
          }
          if (roleError.isBlank() && result.roles().isEmpty()) {
            roleError = "No Roles in this database.";
          }
          fillClientId(result.items(), preferClient, clientError);
          fillAdminRole(result.roles(), preferRole, roleError);
        } catch (Exception e) {
          fillClientId(java.util.List.of(), preferClient, e.getMessage());
          fillAdminRole(java.util.List.of(), preferRole, e.getMessage());
        } finally {
          clientId.setEnabled(true);
          adminRole.setEnabled(true);
        }
      }
    }.execute();
  }

  /** Current identity store as projected into idserver-local.yml (for Manage database). */
  private LocalConfigFile.Form jdbcSnapshot() {
    try {
      String overlay = LocalConfigFile.read(file);
      String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
      return LocalConfigFile.load(overlay, privateYaml);
    } catch (Exception e) {
      LocalConfigFile.Form form = new LocalConfigFile.Form();
      JdbcConnectionStore.Connection active = connStore.active();
      if (active != null) {
        JdbcConnectionStore.applyToForm(active, form);
      }
      return form;
    }
  }

  private String currentClientId() {
    Object selected = clientId.getSelectedItem();
    if (selected instanceof JdbcClientChoices.Item item) {
      return item.clientId();
    }
    return "";
  }

  private String currentAdminRole() {
    Object selected = adminRole.getSelectedItem();
    return selected == null ? "" : selected.toString();
  }

  private void fillClientId(
      java.util.List<JdbcClientChoices.Item> items, String prefer, String error) {
    DefaultComboBoxModel<JdbcClientChoices.Item> model = new DefaultComboBoxModel<>();
    if (items != null) {
      for (JdbcClientChoices.Item item : items) {
        model.addElement(item);
      }
    }
    clientId.setModel(model);
    JdbcClientChoices.Item pick =
        JdbcClientChoices.preferred(items == null ? java.util.List.of() : items, prefer);
    if (pick != null) {
      clientId.setSelectedItem(pick);
    }
    if (error != null && !error.isBlank()) {
      clientIdStatus.setText(error);
    } else {
      int n = model.getSize();
      clientIdStatus.setText(n + " enabled client" + (n == 1 ? "" : "s") + " from the database");
    }
  }

  private void fillAdminRole(java.util.List<String> roles, String prefer, String error) {
    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    if (roles != null) {
      for (String role : roles) {
        model.addElement(role);
      }
    }
    adminRole.setModel(model);
    String pick = JdbcClientChoices.preferredRole(roles == null ? java.util.List.of() : roles, prefer);
    if (pick != null) {
      adminRole.setSelectedItem(pick);
    }
    if (error != null && !error.isBlank()) {
      adminRoleStatus.setText(error);
    } else {
      int n = model.getSize();
      adminRoleStatus.setText(n + " role" + (n == 1 ? "" : "s") + " from the database");
    }
  }

  private static JPanel newForm() {
    JPanel form = ConsoleLook.form();
    form.setBackground(ConsoleLook.PAPER);
    form.setOpaque(true);
    return form;
  }

  private static GridBagConstraints gc() {
    return ConsoleLook.formGc();
  }

  private static void addNote(JPanel form, GridBagConstraints gc, String text) {
    ConsoleLook.addNote(form, gc, text);
  }

  private static void addProcessRows(JPanel form, GridBagConstraints gc, ProcessFields fields) {
    fields.protocol.setEditable(false);
    ConsoleLook.addRow(form, gc, "Protocol", fields.protocol);
    ConsoleLook.addRow(form, gc, "Listen port", fields.port);
    ConsoleLook.addNote(form, gc, "TCP port this process binds. Health checks use localhost plus this port.");
    ConsoleLook.addRow(form, gc, "Java heap (MB)", fields.heapMb);
    ConsoleLook.addNote(form, gc, "Passed as -Xmx to this process only. 512 is a reasonable default.");
    ConsoleLook.addRow(form, gc, "DB pool size", fields.dbPool);
    ConsoleLook.addNote(
        form, gc, "Hikari pool for this process. SQLite should stay 1–8; SQL Server can go higher.");
  }

  private static void fillProcess(ProcessFields fields, LocalConfigFile.ProcessRuntime runtime) {
    LocalConfigFile.ProcessRuntime r = runtime == null ? new LocalConfigFile.ProcessRuntime() : runtime;
    fields.protocol.setSelectedItem(r.sslEnabled ? "HTTPS" : "HTTP");
    fields.port.setText(Integer.toString(r.port));
    fields.heapMb.setText(Integer.toString(r.heapMb));
    fields.dbPool.setText(Integer.toString(r.dbPoolSize));
  }

  private static LocalConfigFile.ProcessRuntime readProcess(ProcessFields fields) {
    boolean ssl = !"HTTP".equalsIgnoreCase(String.valueOf(fields.protocol.getSelectedItem()));
    int port = LocalConfigFile.parseInt(fields.port.getText(), 0);
    int heap = LocalConfigFile.parseInt(fields.heapMb.getText(), LocalConfigFile.DEFAULT_HEAP_MB);
    int pool = LocalConfigFile.parseInt(fields.dbPool.getText(), LocalConfigFile.DEFAULT_DB_POOL);
    return LocalConfigFile.ProcessRuntime.of(port <= 0 ? 8080 : port, ssl, heap, pool);
  }

  private static void addRow(JPanel form, GridBagConstraints gc, String label, java.awt.Component field) {
    ConsoleLook.addRow(form, gc, label, field);
  }

  private static final class ProcessFields {
    final JComboBox<String> protocol = new JComboBox<>(LocalConfigFile.PROTOCOLS);
    final JTextField port = new JTextField();
    final JTextField heapMb = new JTextField();
    final JTextField dbPool = new JTextField();
  }

}
