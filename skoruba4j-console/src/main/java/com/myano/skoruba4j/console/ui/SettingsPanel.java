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
  private JLabel languageSection;
  private JButton neuButton;
  private JButton editButton;
  private JButton dupButton;
  private JButton delButton;
  private JButton activateButton;
  private JButton loadCatalogButton;
  private final List<Runnable> localeRefresh = new ArrayList<>();
  private final JTextField issuerUri = new JTextField();
  private final JComboBox<String> endSession = new JComboBox<>(LocalConfigFile.END_SESSION_MODES);
  private final JComboBox<String> accountChooser = new JComboBox<>();
  private final JComboBox<String> debugMode = new JComboBox<>();
  private final JPasswordField debugPassword = new JPasswordField();
  private final JCheckBox showDebugPassword = new JCheckBox();
  /** Maps to idserver.admin.login-mode (and oidc-enabled for sts-oidc only). Index order fixed. */
  private final JComboBox<String> adminSignIn = new JComboBox<>();
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
  private final JCheckBox showTlsPassword = new JCheckBox();
  private final JTextField smtpHost = new JTextField();
  private final JTextField smtpPort = new JTextField();
  private final JTextField smtpUsername = new JTextField();
  private final JPasswordField smtpPassword = new JPasswordField();
  private final JTextField smtpFrom = new JTextField();
  private final JCheckBox smtpStartTls = new JCheckBox();
  private final JPasswordField smtpResetKey = new JPasswordField();
  private final JCheckBox showSmtpPassword = new JCheckBox();
  private final ProcessFields stsFields = new ProcessFields();
  private final ProcessFields adminFields = new ProcessFields();
  private final ProcessFields apiFields = new ProcessFields();
  private final JComboBox<String> adminApiUi = new JComboBox<>();
  private final JComboBox<String> adminApiLoginMode = new JComboBox<>();
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
  private final JCheckBox showExternalSecrets = new JCheckBox();
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
    refreshLocalizedCombos();
    tabs.addTab(Messages.t("control.tab.database"), scroll(databasePanel()));
    tabs.addTab(Messages.t("control.tab.tls"), scroll(tlsPanel()));
    tabs.addTab(Messages.t("control.tab.sts"), scroll(stsPanel()));
    tabs.addTab(Messages.t("control.tab.external"), scroll(externalPanel()));
    tabs.addTab(Messages.t("control.tab.admin"), scroll(adminPanel()));
    tabs.addTab(Messages.t("control.tab.adminApi"), scroll(apiPanel()));
    tabs.addTab(Messages.t("control.tab.language"), scroll(languagePanel()));
    ConsoleLook.styleTabbedPane(tabs);
    add(tabs, BorderLayout.CENTER);
    saveButton.setToolTipText(Messages.t("control.save.tooltip"));
    saveButton.addActionListener(e -> save());
    JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    south.setOpaque(false);
    south.setBorder(new EmptyBorder(6, 0, 0, 0));
    south.add(saveButton);
    add(south, BorderLayout.SOUTH);
    reload();
  }

  /** Refresh Settings chrome and form labels after the Control language changes. */
  void applyLocale() {
    tabs.setTitleAt(0, Messages.t("control.tab.database"));
    tabs.setTitleAt(1, Messages.t("control.tab.tls"));
    tabs.setTitleAt(2, Messages.t("control.tab.sts"));
    tabs.setTitleAt(3, Messages.t("control.tab.external"));
    tabs.setTitleAt(4, Messages.t("control.tab.admin"));
    tabs.setTitleAt(5, Messages.t("control.tab.adminApi"));
    tabs.setTitleAt(6, Messages.t("control.tab.language"));
    saveButton.setText(Messages.t("control.save"));
    saveButton.setToolTipText(Messages.t("control.save.tooltip"));
    connectionsModel.setColumnIdentifiers(
        new Object[] {
          Messages.t("control.db.col.name"),
          Messages.t("control.db.col.type"),
          Messages.t("control.db.col.url"),
          Messages.t("control.db.col.user"),
          Messages.t("control.db.col.status"),
          ""
        });
    // setColumnIdentifiers rebuilds TableColumnModel and drops custom editors.
    installManageColumn();
    if (neuButton != null) {
      neuButton.setText(Messages.t("control.db.new"));
      editButton.setText(Messages.t("control.db.edit"));
      dupButton.setText(Messages.t("control.db.duplicate"));
      delButton.setText(Messages.t("control.db.delete"));
      activateButton.setText(Messages.t("control.db.setCurrent"));
    }
    languageApply.setText(Messages.t("control.lang.apply"));
    if (languageSection != null) {
      languageSection.setText(Messages.t("control.lang.title"));
    }
    languageNote.setText(ConsoleLook.noteHtml(Messages.t("control.lang.note")));
    refreshLocalizedCombos();
    for (Runnable refresh : localeRefresh) {
      refresh.run();
    }
    syncingLang = true;
    try {
      languageCombo.setSelectedItem(UiLocale.current());
    } finally {
      syncingLang = false;
    }
    languageStatus.setText("");
    refreshConnectionList(selectedId);
  }

  /** Rebuilds choice / sign-in combo labels while preserving selected indexes. */
  private void refreshLocalizedCombos() {
    refillIndexedCombo(
        accountChooser,
        Messages.t("control.choice.yes"),
        Messages.t("control.choice.no"));
    refillIndexedCombo(
        debugMode, Messages.t("control.choice.off"), Messages.t("control.choice.on"));
    refillIndexedCombo(
        adminApiUi, Messages.t("control.choice.yes"), Messages.t("control.choice.no"));
    refillIndexedCombo(
        adminSignIn,
        Messages.t("control.admin.signIn.local"),
        Messages.t("control.admin.signIn.stsPassword"),
        Messages.t("control.admin.signIn.stsOidc"));
    refillIndexedCombo(
        adminApiLoginMode,
        Messages.t("control.api.uiSignIn.both"),
        Messages.t("control.api.uiSignIn.local"),
        Messages.t("control.api.uiSignIn.stsOidc"));
  }

  private static void refillIndexedCombo(JComboBox<String> box, String... labels) {
    int idx = box.getSelectedIndex();
    box.removeAllItems();
    for (String label : labels) {
      box.addItem(label);
    }
    if (box.getItemCount() > 0) {
      box.setSelectedIndex(Math.max(0, Math.min(idx < 0 ? 0 : idx, box.getItemCount() - 1)));
    }
  }

  private JPanel languagePanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    languageSection = ConsoleLook.addSection(form, gc, Messages.t("control.lang.title"));
    gc.gridy++;
    gc.gridx = 0;
    gc.gridwidth = 2;
    languageNote.setText(ConsoleLook.noteHtml(Messages.t("control.lang.note")));
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
    i18nRow(form, gc, "lang.choose", languageCombo);
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

    i18nSection(form, gc, "control.db.connections");
    i18nNote(form, gc, "control.db.note");

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
    installManageColumn();
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
    i18nNote(form, gc, "control.tls.intro");
    i18nBrowseRow(form, gc, "control.tls.keyStore", keyStore);
    i18nRow(form, gc, "control.tls.keyStorePassword", keyStorePassword);
    i18nRow(form, gc, "control.tls.keyStoreType", keyStoreType);
    keyStoreType.setEditable(false);
    i18nRow(form, gc, "control.tls.keyAlias", keyAlias);
    i18nBrowseRow(form, gc, "control.tls.trustStore", trustStore);
    i18nRow(form, gc, "control.tls.trustStorePassword", trustStorePassword);
    i18nRow(form, gc, "control.tls.trustStoreType", trustStoreType);
    trustStoreType.setEditable(false);
    gc.gridy++;
    gc.gridx = 1;
    i18nCheck(showTlsPassword, "control.tls.showPasswords");
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
    i18nNote(form, gc, "control.sts.intro");
    addProcessRows(form, gc, stsFields);
    accountChooser.setEditable(false);
    i18nRow(form, gc, "control.sts.accountChooser", accountChooser);
    i18nNote(form, gc, "control.sts.accountChooser.note");
    debugMode.setEditable(false);
    i18nRow(form, gc, "control.sts.debugLogin", debugMode);
    i18nRow(form, gc, "control.sts.debugPassword", debugPassword);
    gc.gridy++;
    gc.gridx = 1;
    i18nCheck(showDebugPassword, "control.sts.showDebugPassword");
    showDebugPassword.addActionListener(
        e -> debugPassword.setEchoChar(showDebugPassword.isSelected() ? 0 : hiddenEcho));
    form.add(showDebugPassword, gc);
    i18nNote(form, gc, "control.sts.debug.note");
    endSession.setEditable(false);
    i18nRow(form, gc, "control.sts.endSession", endSession);
    i18nNote(form, gc, "control.sts.endSession.note");
    i18nNote(form, gc, "control.sts.smtp.intro");
    i18nRow(form, gc, "control.sts.smtp.host", smtpHost);
    i18nRow(form, gc, "control.sts.smtp.port", smtpPort);
    i18nRow(form, gc, "control.sts.smtp.username", smtpUsername);
    i18nRow(form, gc, "control.sts.smtp.password", smtpPassword);
    i18nRow(form, gc, "control.sts.smtp.from", smtpFrom);
    gc.gridy++;
    gc.gridx = 1;
    i18nCheck(smtpStartTls, "control.sts.smtp.startTls");
    form.add(smtpStartTls, gc);
    i18nRow(form, gc, "control.sts.smtp.resetKey", smtpResetKey);
    gc.gridy++;
    gc.gridx = 1;
    i18nCheck(showSmtpPassword, "control.sts.showSmtpSecrets");
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
    i18nNote(form, gc, "control.admin.intro");
    addProcessRows(form, gc, adminFields);
    i18nRow(form, gc, "control.admin.issuer", issuerUri);
    i18nNote(form, gc, "control.admin.issuer.note");
    adminSignIn.setEditable(false);
    i18nRow(form, gc, "control.admin.signIn", adminSignIn);
    i18nNote(form, gc, "control.admin.signIn.note");
    adminSignIn.addActionListener(e -> syncAdminSignInFields());
    clientId.setEditable(false);
    addClientIdRow(form, gc);
    i18nRow(form, gc, "control.admin.clientSecret", clientSecret);
    i18nNote(form, gc, "control.admin.clientSecret.note");
    adminRole.setEditable(false);
    addAdminRoleRow(form, gc);
    return form;
  }

  private JPanel apiPanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    i18nNote(form, gc, "control.api.intro");
    addProcessRows(form, gc, apiFields);
    adminApiUi.setEditable(false);
    i18nRow(form, gc, "control.api.enableUi", adminApiUi);
    i18nNote(form, gc, "control.api.enableUi.note");
    adminApiLoginMode.setEditable(false);
    i18nRow(form, gc, "control.api.uiSignIn", adminApiLoginMode);
    i18nNote(form, gc, "control.api.uiSignIn.note");
    i18nRow(form, gc, "control.api.clientId", adminApiClientId);
    i18nRow(form, gc, "control.api.clientSecret", adminApiClientSecret);
    i18nNote(form, gc, "control.api.clientSecret.note");
    return form;
  }

  private JPanel externalPanel() {
    JPanel form = newForm();
    GridBagConstraints gc = gc();
    i18nNote(form, gc, "control.ext.intro");
    i18nSection(form, gc, "control.ext.google");
    i18nRow(form, gc, "control.ext.clientId", googleClientId);
    i18nRow(form, gc, "control.ext.clientSecret", googleClientSecret);
    i18nSection(form, gc, "control.ext.microsoft");
    i18nRow(form, gc, "control.ext.clientId", microsoftClientId);
    i18nRow(form, gc, "control.ext.clientSecret", microsoftClientSecret);
    i18nRow(form, gc, "control.ext.tenantId", microsoftTenantId);
    i18nSection(form, gc, "control.ext.whatsapp");
    i18nRow(form, gc, "control.ext.whatsappPhone", whatsappBusinessPhone);
    i18nNote(form, gc, "control.ext.whatsappPhone.note");
    i18nRow(form, gc, "control.ext.webhookToken", whatsappWebhookVerifyToken);
    i18nRow(form, gc, "control.ext.appSecret", whatsappAppSecret);
    i18nSection(form, gc, "control.ext.wechat");
    i18nRow(form, gc, "control.ext.wechatAppId", wechatAppId);
    i18nRow(form, gc, "control.ext.wechatAppSecret", wechatAppSecret);
    i18nNote(form, gc, "control.ext.wechat.note");
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
    i18nCheck(showExternalSecrets, "control.ext.showSecrets");
    gc.gridy++;
    gc.gridx = 1;
    form.add(showExternalSecrets, gc);
    return form;
  }

  void reload() {
    try {
      String overlay = LocalConfigFile.read(file);
      String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
      LocalConfigFile.Form form = LocalConfigFile.load(overlay, privateYaml);
      loadConnectionStore();
      issuerUri.setText(form.issuerUri);
      accountChooser.setSelectedIndex(form.accountChooserEnabled ? 0 : 1);
      debugMode.setSelectedIndex(form.debugMode ? 1 : 0);
      debugPassword.setText(form.debugPassword);
      endSession.setSelectedItem(LocalConfigFile.canonicalizeEndSession(form.endSession));
      selectLoginMode(form.loginMode);
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
      adminApiUi.setSelectedIndex(form.adminApiUiEnabled ? 0 : 1);
      selectApiLoginMode(form.adminApiLoginMode);
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
    return switch (adminSignIn.getSelectedIndex()) {
      case 0 -> "local";
      case 2 -> "sts-oidc";
      default -> "sts-password";
    };
  }

  private void selectLoginMode(String mode) {
    adminSignIn.setSelectedIndex(
        switch (LocalConfigFile.canonicalizeLoginMode(mode)) {
          case "local" -> 0;
          case "sts-oidc" -> 2;
          default -> 1;
        });
  }

  private boolean usesStsClient() {
    String mode = selectedLoginMode();
    return "sts-password".equals(mode) || "sts-oidc".equals(mode);
  }

  private String selectedApiLoginMode() {
    return switch (adminApiLoginMode.getSelectedIndex()) {
      case 1 -> "local";
      case 2 -> "sts-oidc";
      default -> "both";
    };
  }

  private void selectApiLoginMode(String mode) {
    adminApiLoginMode.setSelectedIndex(
        switch (LocalConfigFile.canonicalizeApiLoginMode(mode)) {
          case "local" -> 1;
          case "sts-oidc" -> 2;
          default -> 0;
        });
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
      form.accountChooserEnabled = accountChooser.getSelectedIndex() != 1;
      form.debugMode = debugMode.getSelectedIndex() == 1;
      form.debugPassword = new String(debugPassword.getPassword());
      form.endSession = (String) endSession.getSelectedItem();
      form.loginMode = selectedLoginMode();
      form.oidcEnabled = "sts-oidc".equals(form.loginMode);
      JdbcClientChoices.Item selected = (JdbcClientChoices.Item) clientId.getSelectedItem();
      if (usesStsClient()
          && (selected == null || selected.clientId() == null || selected.clientId().isBlank())) {
        JOptionPane.showMessageDialog(
            this, Messages.t("control.admin.selectClient"));
        return;
      }
      form.clientId = selected == null ? "" : selected.clientId();
      form.clientSecret = new String(clientSecret.getPassword());
      String role = (String) adminRole.getSelectedItem();
      if (usesStsClient() && (role == null || role.isBlank())) {
        JOptionPane.showMessageDialog(this, Messages.t("control.admin.selectRole"));
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
      form.adminApiUiEnabled = adminApiUi.getSelectedIndex() != 1;
      form.adminApiLoginMode = selectedApiLoginMode();
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
      JOptionPane.showMessageDialog(
          this, Messages.t("control.db.loadFailed", e.getMessage()));
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
      JOptionPane.showMessageDialog(this, Messages.t("control.db.selectFirst"));
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
      JOptionPane.showMessageDialog(this, Messages.t("control.db.selectFirst"));
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
      dbStatus.setText(Messages.t("control.db.saved"));
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
    }
  }

  private void deleteConnection() {
    if (connStore.connections.size() <= 1) {
      JOptionPane.showMessageDialog(this, Messages.t("control.db.keepOne"));
      return;
    }
    JdbcConnectionStore.Connection c = connStore.find(selectedId);
    if (c == null) {
      return;
    }
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            Messages.t("control.db.deleteConfirm", c.name),
            Messages.t("control.db.deleteTitle"),
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
      dbStatus.setText(Messages.t("control.db.deleted"));
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage());
    }
  }

  private void setAsCurrent() {
    JdbcConnectionStore.Connection c = connStore.find(selectedId);
    if (c == null) {
      JOptionPane.showMessageDialog(this, Messages.t("control.db.selectFirst"));
      return;
    }
    connStore.activeId = c.id;
    try {
      JdbcConnectionStore.save(connectionsFile, connStore);
      projectActiveToLocalYaml();
      refreshConnectionList(c.id);
      loadCatalogFromDb(currentClientId(), currentAdminRole());
      dbStatus.setForeground(ConsoleLook.UP);
      dbStatus.setText(Messages.t("control.db.setCurrentDone"));
      JOptionPane.showMessageDialog(
          this,
          Messages.t("control.db.setCurrentMsg").replace("\\n", "\n"),
          Messages.t("control.db.setCurrentTitle"),
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
      JOptionPane.showMessageDialog(this, Messages.t("control.db.selectFirst"));
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

  /**
   * Binds the Manage action column. Must run again after {@link
   * DefaultTableModel#setColumnIdentifiers} (locale refresh) recreates columns.
   */
  private void installManageColumn() {
    if (connectionsTable.getColumnCount() < 6) {
      return;
    }
    var column = connectionsTable.getColumnModel().getColumn(5);
    column.setPreferredWidth(88);
    column.setMaxWidth(100);
    column.setCellRenderer(new ManageButtonRenderer());
    column.setCellEditor(new ManageButtonEditor());
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

  private void i18nBrowseRow(JPanel form, GridBagConstraints gc, String key, JTextField field) {
    gc.gridy++;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    JLabel left = ConsoleLook.fieldLabel(Messages.t(key));
    form.add(left, gc);
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    row.add(field, BorderLayout.CENTER);
    JButton browse = ConsoleLook.primary(Messages.t("control.browse"));
    browse.addActionListener(e -> browseStore(field));
    row.add(browse, BorderLayout.EAST);
    form.add(row, gc);
    localeRefresh.add(
        () -> {
          left.setText(Messages.t(key));
          browse.setText(Messages.t("control.browse"));
        });
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
    JLabel left = ConsoleLook.fieldLabel(Messages.t("control.admin.clientId"));
    form.add(left, gc);
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    row.add(clientId, BorderLayout.CENTER);
    loadCatalogButton = ConsoleLook.primary(Messages.t("control.admin.loadCatalog"));
    loadCatalogButton.addActionListener(e -> loadCatalogFromDb(currentClientId(), currentAdminRole()));
    row.add(loadCatalogButton, BorderLayout.EAST);
    form.add(row, gc);
    gc.gridy++;
    gc.gridx = 1;
    clientIdStatus.setFont(ConsoleLook.uiSmall());
    clientIdStatus.setForeground(ConsoleLook.MUTED);
    form.add(clientIdStatus, gc);
    localeRefresh.add(
        () -> {
          left.setText(Messages.t("control.admin.clientId"));
          if (loadCatalogButton != null) {
            loadCatalogButton.setText(Messages.t("control.admin.loadCatalog"));
          }
        });
  }

  private void addAdminRoleRow(JPanel form, GridBagConstraints gc) {
    gc.gridy++;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    JLabel left = ConsoleLook.fieldLabel(Messages.t("control.admin.role"));
    form.add(left, gc);
    gc.gridx = 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    form.add(adminRole, gc);
    gc.gridy++;
    gc.gridx = 1;
    adminRoleStatus.setFont(ConsoleLook.uiSmall());
    adminRoleStatus.setForeground(ConsoleLook.MUTED);
    form.add(adminRoleStatus, gc);
    localeRefresh.add(() -> left.setText(Messages.t("control.admin.role")));
  }

  private void loadCatalogFromDb(String preferClient, String preferRole) {
    LocalConfigFile.Form snapshot = jdbcSnapshot();
    clientId.setEnabled(false);
    adminRole.setEnabled(false);
    clientIdStatus.setText(Messages.t("control.admin.loadingClients"));
    adminRoleStatus.setText(Messages.t("control.admin.loadingRoles"));
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
            clientError = Messages.t("control.admin.noClients");
          }
          if (roleError.isBlank() && result.roles().isEmpty()) {
            roleError = Messages.t("control.admin.noRoles");
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
      clientIdStatus.setText(Messages.t("control.admin.clientsCount", model.getSize()));
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
      adminRoleStatus.setText(Messages.t("control.admin.rolesCount", model.getSize()));
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

  private void i18nRow(JPanel form, GridBagConstraints gc, String key, java.awt.Component field) {
    JLabel label = ConsoleLook.addRow(form, gc, Messages.t(key), field);
    localeRefresh.add(() -> label.setText(Messages.t(key)));
  }

  private void i18nNote(JPanel form, GridBagConstraints gc, String key) {
    JLabel note = ConsoleLook.addNote(form, gc, Messages.t(key));
    localeRefresh.add(() -> note.setText(ConsoleLook.noteHtml(Messages.t(key))));
  }

  private void i18nSection(JPanel form, GridBagConstraints gc, String key) {
    JLabel section = ConsoleLook.addSection(form, gc, Messages.t(key));
    localeRefresh.add(() -> section.setText(Messages.t(key)));
  }

  private void i18nCheck(JCheckBox box, String key) {
    box.setText(Messages.t(key));
    localeRefresh.add(() -> box.setText(Messages.t(key)));
  }

  private void addProcessRows(JPanel form, GridBagConstraints gc, ProcessFields fields) {
    fields.protocol.setEditable(false);
    i18nRow(form, gc, "control.process.protocol", fields.protocol);
    i18nRow(form, gc, "control.process.port", fields.port);
    i18nNote(form, gc, "control.process.port.note");
    i18nRow(form, gc, "control.process.heap", fields.heapMb);
    i18nNote(form, gc, "control.process.heap.note");
    i18nRow(form, gc, "control.process.pool", fields.dbPool);
    i18nNote(form, gc, "control.process.pool.note");
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

  private static final class ProcessFields {
    final JComboBox<String> protocol = new JComboBox<>(LocalConfigFile.PROTOCOLS);
    final JTextField port = new JTextField();
    final JTextField heapMb = new JTextField();
    final JTextField dbPool = new JTextField();
  }

}
