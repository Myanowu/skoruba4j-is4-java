package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.console.config.ConsoleProperties;
import com.myano.skoruba4j.console.health.NodeHealthClient;
import com.myano.skoruba4j.console.process.LocalProcessSupervisor;
import com.myano.skoruba4j.console.process.LogTailer;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

final class NodePanel extends JPanel {
  private final String nodeId;
  private final ConsoleProperties.Node node;
  private final Path repoRoot;
  private final LocalProcessSupervisor supervisor;
  private final JLabel status = new JLabel("…");
  private final JLabel healthLine = new JLabel(" ");
  private final JTextArea log = new JTextArea();
  private final JButton start = ConsoleLook.solidButton("Start", ConsoleLook.WINE, java.awt.Color.WHITE);
  private final JButton stop = ConsoleLook.dangerOutline("Stop");
  private boolean up;
  private long logSkipBytes;

  NodePanel(
      String nodeId,
      ConsoleProperties.Node node,
      Path repoRoot,
      LocalProcessSupervisor supervisor) {
    super(new BorderLayout(0, 0));
    this.nodeId = nodeId;
    this.node = node;
    this.repoRoot = repoRoot;
    this.supervisor = supervisor;
    setBackground(ConsoleLook.PAPER);

    JPanel header = new JPanel(new BorderLayout(8, 4));
    header.setBackground(ConsoleLook.CARD);
    header.setBorder(
        javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, ConsoleLook.LINE),
            new EmptyBorder(12, 16, 12, 16)));
    JLabel title = new JLabel(node.getDisplayName());
    title.setFont(ConsoleLook.uiTitle());
    title.setForeground(ConsoleLook.INK);
    JLabel meta =
        new JLabel((node.isLocal() ? "Local" : "Remote") + "  ·  " + node.getHealthUrl());
    meta.setForeground(ConsoleLook.MUTED);
    meta.setFont(ConsoleLook.uiSmall());
    status.setFont(ConsoleLook.ui().deriveFont(Font.BOLD, 14f));
    JPanel titles = new JPanel(new BorderLayout(4, 2));
    titles.setOpaque(false);
    titles.add(title, BorderLayout.NORTH);
    titles.add(meta, BorderLayout.CENTER);
    header.add(titles, BorderLayout.CENTER);
    header.add(status, BorderLayout.EAST);

    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    bar.setOpaque(true);
    bar.setBackground(ConsoleLook.PAPER);
    bar.setBorder(new EmptyBorder(10, 14, 8, 14));
    JButton browse = ConsoleLook.outlineButton("Open in browser", ConsoleLook.WINE, ConsoleLook.WINE);
    browse.setPreferredSize(new java.awt.Dimension(140, 34));
    JButton clearLogs = ConsoleLook.outlineButton("Clear logs", ConsoleLook.LINE, ConsoleLook.INK);
    start.addActionListener(e -> runAsync(this::start));
    stop.addActionListener(e -> runAsync(this::stop));
    clearLogs.addActionListener(e -> clearLogs());
    browse.addActionListener(
        e -> BrowserLaunch.open(NodePanel.this, node.getHealthUrl(), node.getModule(), repoRoot));
    bar.add(start);
    bar.add(stop);
    bar.add(browse);
    bar.add(clearLogs);
    syncActionButtons(false);

    healthLine.setForeground(ConsoleLook.MUTED);
    healthLine.setFont(ConsoleLook.mono().deriveFont(11f));
    healthLine.setBorder(new EmptyBorder(0, 16, 8, 16));

    JPanel north = new JPanel(new BorderLayout());
    north.setOpaque(false);
    north.add(header, BorderLayout.NORTH);
    north.add(bar, BorderLayout.CENTER);
    north.add(healthLine, BorderLayout.SOUTH);
    add(north, BorderLayout.NORTH);

    log.setEditable(false);
    log.setFont(ConsoleLook.mono());
    log.setBackground(new java.awt.Color(0x2A181A));
    log.setForeground(new java.awt.Color(0xF0E4C8));
    log.setCaretColor(new java.awt.Color(0xF0E4C8));
    log.setBorder(new EmptyBorder(10, 12, 10, 12));
    add(new JScrollPane(log), BorderLayout.CENTER);
  }

  String displayName() {
    return node.getDisplayName();
  }

  boolean up() {
    return up;
  }

  void refresh() {
    String body = NodeHealthClient.fetch(node.getHealthUrl());
    up = body != null && body.contains("\"status\"") && body.toUpperCase().contains("UP");
    boolean here = supervisor.startedHere(nodeId);
    boolean processLive = supervisor.processAlive(nodeId, node);
    boolean running = up || processLive;
    status.setText(up ? "UP" : (processLive ? "STARTING" : "DOWN"));
    status.setForeground(up ? ConsoleLook.UP : (processLive ? ConsoleLook.WINE : ConsoleLook.DOWN));
    String snippet = oneLine(body);
    if (snippet.length() > 160) {
      snippet = snippet.substring(0, 157) + "…";
    }
    healthLine.setText((here ? "started-here  " : "") + snippet);
    syncActionButtons(running);
    Path file = logFile();
    if (logSkipBytes > LogTailer.size(file)) {
      logSkipBytes = 0;
    }
    try {
      String tail = LogTailer.tailSkipping(file, logSkipBytes, 24_000);
      if (!tail.equals(log.getText())) {
        log.setText(tail);
        log.setCaretPosition(log.getDocument().getLength());
      }
    } catch (Exception e) {
      append("Log: " + e.getMessage());
    }
  }

  /** Solid emphasis follows the available action; Start disabled while the process is live. */
  private void syncActionButtons(boolean running) {
    if (!node.isLocal()) {
      start.setEnabled(false);
      stop.setEnabled(false);
      ConsoleLook.applyOutline(start, ConsoleLook.LINE, ConsoleLook.MUTED);
      ConsoleLook.applyOutline(stop, ConsoleLook.LINE, ConsoleLook.MUTED);
      start.setCursor(Cursor.getDefaultCursor());
      stop.setCursor(Cursor.getDefaultCursor());
      return;
    }
    if (running) {
      ConsoleLook.applyOutline(start, ConsoleLook.LINE, ConsoleLook.MUTED);
      ConsoleLook.applySolid(stop, ConsoleLook.DOWN, java.awt.Color.WHITE);
      start.setEnabled(false);
      stop.setEnabled(true);
      start.setCursor(Cursor.getDefaultCursor());
      stop.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    } else {
      ConsoleLook.applySolid(start, ConsoleLook.WINE, java.awt.Color.WHITE);
      ConsoleLook.applyOutline(stop, ConsoleLook.DOWN, ConsoleLook.DOWN);
      start.setEnabled(true);
      stop.setEnabled(false);
      start.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      stop.setCursor(Cursor.getDefaultCursor());
    }
  }

  private void clearLogs() {
    Path file = logFile();
    log.setText("");
    try {
      LogTailer.truncate(file);
      logSkipBytes = 0;
    } catch (Exception e) {
      logSkipBytes = LogTailer.size(file);
    }
  }

  private Path logFile() {
    return repoRoot.resolve("logs").resolve(node.getModule() + ".log");
  }

  private void start() {
    SwingUtilities.invokeLater(() -> {
      start.setEnabled(false);
      stop.setEnabled(false);
    });
    try {
      Optional<String> error = supervisor.start(nodeId, node, repoRoot);
      SwingUtilities.invokeLater(
          () -> {
            if (error.isPresent()) {
              append("Start: " + error.get());
              JOptionPane.showMessageDialog(
                  NodePanel.this, error.get(), "Start failed", JOptionPane.WARNING_MESSAGE);
            } else {
              append("Start requested: " + node.getModule());
            }
            refresh();
          });
    } catch (Exception e) {
      SwingUtilities.invokeLater(
          () -> {
            append("Start error: " + e.getMessage());
            JOptionPane.showMessageDialog(
                NodePanel.this, e.getMessage(), "Start failed", JOptionPane.ERROR_MESSAGE);
            refresh();
          });
    }
  }

  private void stop() {
    SwingUtilities.invokeLater(() -> {
      start.setEnabled(false);
      stop.setEnabled(false);
    });
    Optional<String> error = supervisor.stop(nodeId, node);
    SwingUtilities.invokeLater(
        () -> {
          if (error.isPresent()) {
            append("Stop: " + error.get());
            JOptionPane.showMessageDialog(
                NodePanel.this, error.get(), "Stop", JOptionPane.WARNING_MESSAGE);
          } else {
            append("Stop requested.");
          }
          refresh();
        });
  }

  private void append(String line) {
    log.append("\n[control] " + line + "\n");
  }

  private static void runAsync(Runnable work) {
    new Thread(work, "skoruba4j-control-action").start();
  }

  private static String oneLine(String body) {
    if (body == null) {
      return "";
    }
    return body.replace('\n', ' ').replace('\r', ' ');
  }
}
