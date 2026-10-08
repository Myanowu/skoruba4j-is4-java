package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.console.config.ConsoleProperties;
import com.myano.skoruba4j.console.configfile.JdbcConnectionStore;
import com.myano.skoruba4j.console.health.NodeHealthClient;
import com.myano.skoruba4j.console.process.LocalProcessSupervisor;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import com.myano.skoruba4j.i18n.Messages;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Dashboard of process cards — Docker Desktop / host-manager style. */
final class OverviewPanel extends JPanel {
  private final Path repoRoot;
  private final Map<String, ConsoleProperties.Node> nodes;
  private final LocalProcessSupervisor supervisor;
  private final List<Card> cards = new ArrayList<>();
  private final JLabel heading = new JLabel();
  private final JLabel hint = new JLabel();
  private final JLabel storeLabel = new JLabel(" ");
  private final JLabel loginHint = new JLabel(" ");
  private final JButton startAll = ConsoleLook.primary(Messages.t("control.overview.startAll"));
  private final JButton stopAll =
      ConsoleLook.outlineButton(Messages.t("control.overview.stopAll"), ConsoleLook.LINE, ConsoleLook.INK);

  OverviewPanel(
      Path repoRoot,
      Map<String, ConsoleProperties.Node> nodes,
      LocalProcessSupervisor supervisor,
      Consumer<String> openNode) {
    super(new BorderLayout(16, 16));
    this.repoRoot = repoRoot;
    this.nodes = nodes;
    this.supervisor = supervisor;
    setBackground(ConsoleLook.PAPER);
    setBorder(new EmptyBorder(24, 24, 24, 24));
    heading.setFont(ConsoleLook.uiTitle());
    heading.setForeground(ConsoleLook.INK);
    hint.setForeground(ConsoleLook.MUTED);
    hint.setFont(ConsoleLook.uiSmall());
    storeLabel.setFont(ConsoleLook.ui());
    storeLabel.setForeground(ConsoleLook.WINE);
    loginHint.setFont(ConsoleLook.uiSmall());
    loginHint.setForeground(ConsoleLook.MUTED);
    applyLocaleTexts();
    JPanel north = new JPanel(new BorderLayout(0, 6));
    north.setOpaque(false);
    north.add(heading, BorderLayout.NORTH);
    JPanel sub = new JPanel(new BorderLayout(0, 4));
    sub.setOpaque(false);
    sub.add(hint, BorderLayout.NORTH);
    JPanel storeBlock = new JPanel(new BorderLayout(0, 2));
    storeBlock.setOpaque(false);
    storeBlock.add(storeLabel, BorderLayout.NORTH);
    storeBlock.add(loginHint, BorderLayout.SOUTH);
    sub.add(storeBlock, BorderLayout.SOUTH);
    north.add(sub, BorderLayout.CENTER);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actions.setOpaque(false);
    startAll.addActionListener(e -> startAll());
    stopAll.addActionListener(e -> stopAll());
    actions.add(startAll);
    actions.add(stopAll);
    north.add(actions, BorderLayout.SOUTH);
    add(north, BorderLayout.NORTH);

    int n = nodes == null ? 1 : Math.max(1, nodes.size());
    JPanel grid = new JPanel(new GridLayout(1, n, 16, 16));
    grid.setOpaque(false);
    if (nodes != null) {
      for (Map.Entry<String, ConsoleProperties.Node> entry : nodes.entrySet()) {
        Card card = new Card(entry.getKey(), entry.getValue(), supervisor, repoRoot, openNode);
        cards.add(card);
        grid.add(card);
      }
    }
    grid.setPreferredSize(new java.awt.Dimension(10, 260));
    JPanel wrap = new JPanel(new BorderLayout());
    wrap.setOpaque(false);
    wrap.add(grid, BorderLayout.NORTH);
    add(wrap, BorderLayout.CENTER);
    refreshStoreLabel();
  }

  /** Refresh overview chrome after Control language changes. */
  void applyLocale() {
    applyLocaleTexts();
    for (Card card : cards) {
      card.applyLocale();
    }
    refreshStoreLabel();
  }

  private void applyLocaleTexts() {
    heading.setText(Messages.t("control.overview.title"));
    hint.setText(Messages.t("control.overview.hint"));
    startAll.setText(Messages.t("control.overview.startAll"));
    stopAll.setText(Messages.t("control.overview.stopAll"));
    loginHint.setText(SqliteSchema.demoLoginHint() + "  " + Messages.t("control.overview.loginDetails"));
  }

  void refresh() {
    refreshStoreLabel();
    for (Card card : cards) {
      card.refresh();
    }
  }

  private void refreshStoreLabel() {
    try {
      JdbcConnectionStore.Store store = JdbcConnectionStore.loadOrSeed(repoRoot);
      JdbcConnectionStore.Connection active = store.active();
      if (active == null) {
        storeLabel.setText(Messages.t("control.overview.storeNone"));
        return;
      }
      String name = active.name == null || active.name.isBlank() ? active.id : active.name;
      String provider = active.provider == null ? "?" : active.provider;
      storeLabel.setText(Messages.t("control.overview.store", name, provider));
      storeLabel.setToolTipText(active.url);
    } catch (Exception e) {
      storeLabel.setText(Messages.t("control.overview.storeError"));
      storeLabel.setToolTipText(e.getMessage());
    }
  }

  private void startAll() {
    if (nodes == null || nodes.isEmpty()) {
      return;
    }
    StringBuilder errors = new StringBuilder();
    int started = 0;
    for (Map.Entry<String, ConsoleProperties.Node> entry : nodes.entrySet()) {
      if (!entry.getValue().isLocal()) {
        continue;
      }
      try {
        Optional<String> err = supervisor.start(entry.getKey(), entry.getValue(), repoRoot);
        if (err.isEmpty()) {
          started++;
        } else {
          errors.append(entry.getValue().getDisplayName()).append(": ").append(err.get()).append('\n');
        }
      } catch (Exception e) {
        errors.append(entry.getValue().getDisplayName()).append(": ").append(e.getMessage()).append('\n');
      }
    }
    refresh();
    if (errors.length() > 0) {
      JOptionPane.showMessageDialog(
          this,
          "Started " + started + " process(es).\n\n" + errors,
          Messages.t("control.overview.startAll"),
          JOptionPane.WARNING_MESSAGE);
    }
  }

  private void stopAll() {
    if (nodes == null || nodes.isEmpty()) {
      return;
    }
    int ok =
        JOptionPane.showConfirmDialog(
            this,
            "Stop all local STS / Admin / Admin API processes?",
            Messages.t("control.overview.stopAll"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (ok != JOptionPane.OK_OPTION) {
      return;
    }
    StringBuilder errors = new StringBuilder();
    int stopped = 0;
    for (Map.Entry<String, ConsoleProperties.Node> entry : nodes.entrySet()) {
      if (!entry.getValue().isLocal()) {
        continue;
      }
      Optional<String> err = supervisor.stop(entry.getKey(), entry.getValue());
      if (err.isEmpty()) {
        stopped++;
      } else {
        errors.append(entry.getValue().getDisplayName()).append(": ").append(err.get()).append('\n');
      }
    }
    refresh();
    if (errors.length() > 0) {
      JOptionPane.showMessageDialog(
          this,
          "Stop attempted for " + stopped + " process(es).\n\n" + errors,
          Messages.t("control.overview.stopAll"),
          JOptionPane.WARNING_MESSAGE);
    }
  }

  private static final class Card extends JPanel {
    private final String nodeId;
    private final ConsoleProperties.Node node;
    private final LocalProcessSupervisor supervisor;
    private final Path repoRoot;
    private final JLabel where = new JLabel();
    private final JLabel status = new JLabel("…");
    private final JLabel detail = new JLabel(" ");
    private final JButton open =
        ConsoleLook.outlineButton(Messages.t("control.card.logs"), ConsoleLook.LINE, ConsoleLook.INK);
    private final JButton browse =
        ConsoleLook.outlineButton(Messages.t("control.card.browser"), ConsoleLook.WINE, ConsoleLook.WINE);

    Card(
        String nodeId,
        ConsoleProperties.Node node,
        LocalProcessSupervisor supervisor,
        Path repoRoot,
        Consumer<String> openNode) {
      super(new BorderLayout(8, 8));
      this.nodeId = nodeId;
      this.node = node;
      this.supervisor = supervisor;
      this.repoRoot = repoRoot;
      ConsoleLook.card(this);
      JLabel name = new JLabel(node.getDisplayName());
      name.setFont(ConsoleLook.ui().deriveFont(java.awt.Font.BOLD, 15f));
      name.setForeground(ConsoleLook.INK);
      where.setForeground(ConsoleLook.MUTED);
      where.setFont(ConsoleLook.uiSmall());
      applyLocale();
      JPanel top = new JPanel(new BorderLayout(4, 4));
      top.setOpaque(false);
      top.add(name, BorderLayout.NORTH);
      top.add(where, BorderLayout.CENTER);
      add(top, BorderLayout.NORTH);
      status.setFont(ConsoleLook.ui().deriveFont(java.awt.Font.BOLD, 22f));
      detail.setForeground(ConsoleLook.MUTED);
      detail.setFont(ConsoleLook.uiSmall());
      JPanel mid = new JPanel(new BorderLayout(4, 4));
      mid.setOpaque(false);
      mid.add(status, BorderLayout.NORTH);
      mid.add(detail, BorderLayout.CENTER);
      add(mid, BorderLayout.CENTER);
      open.setPreferredSize(new java.awt.Dimension(72, 30));
      open.addActionListener(e -> openNode.accept(nodeId));
      browse.setPreferredSize(new java.awt.Dimension(88, 30));
      browse.addActionListener(
          e -> BrowserLaunch.open(Card.this, node.getHealthUrl(), node.getModule(), repoRoot));
      JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
      south.setOpaque(false);
      south.add(browse);
      south.add(open);
      add(south, BorderLayout.SOUTH);
    }

    void applyLocale() {
      where.setText(
          node.isLocal() ? Messages.t("control.card.local") : Messages.t("control.card.remote"));
      open.setText(Messages.t("control.card.logs"));
      browse.setText(Messages.t("control.card.browser"));
      refresh();
    }

    void refresh() {
      String body = NodeHealthClient.fetch(node.getHealthUrl());
      NodeHealthClient.Summary health = NodeHealthClient.summarize(body);
      boolean here = supervisor.startedHere(nodeId);
      status.setText(health.up() ? "UP" : "DOWN");
      status.setForeground(health.up() ? ConsoleLook.UP : ConsoleLook.DOWN);
      detail.setText(
          "<html><body style='width:220px'>"
              + escape(health.line())
              + "<br>"
              + escape(node.getHealthUrl())
              + (here ? "<br>" + Messages.t("control.card.startedHere") : "")
              + "</body></html>");
    }

    private static String escape(String value) {
      if (value == null) {
        return "";
      }
      return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
  }
}
