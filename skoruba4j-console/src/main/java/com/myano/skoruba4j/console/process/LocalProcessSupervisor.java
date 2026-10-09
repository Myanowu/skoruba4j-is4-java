package com.myano.skoruba4j.console.process;

import com.myano.skoruba4j.console.RepoRoot;
import com.myano.skoruba4j.console.config.ConsoleProperties;
import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Starts fat jars the console itself spawned. Will not SSH to another machine. */
public final class LocalProcessSupervisor {
  private final Map<String, Process> children = new ConcurrentHashMap<>();

  public Optional<String> start(String nodeId, ConsoleProperties.Node node, Path repoRoot)
      throws IOException {
    if (node == null || !node.isLocal()) {
      return Optional.of("Remote nodes cannot be started from this console.");
    }
    Process existing = children.get(nodeId);
    if (existing != null && existing.isAlive()) {
      return Optional.of("Already started by this console (pid " + existing.pid() + ").");
    }
    Set<Long> live = findPids(node);
    if (!live.isEmpty()) {
      return Optional.of("Already running (pid " + live.iterator().next() + "). Stop it first.");
    }
    Path jar = findRunnableJar(repoRoot, node.getModule());
    if (jar == null) {
      return Optional.of("Jar not found. Run mvn package (install tree is dist/lib).");
    }
    Path work = workDir(repoRoot, node.getModule());
    Files.createDirectories(work);
    Path logs = repoRoot.resolve("logs");
    Files.createDirectories(logs);
    ProcessBuilder builder =
        new ProcessBuilder(startCommand(work, jar, node.getModule(), repoRoot));
    builder.directory(work.toFile());
    builder.environment().put("IDSERVER_HOME", repoRoot.toAbsolutePath().toString());
    Path overlay = LocalConfigFile.resolve(repoRoot);
    builder.environment().put("IDSERVER_CONFIG", overlay.toAbsolutePath().toString());
    builder.redirectErrorStream(true);
    builder.redirectOutput(logs.resolve(node.getModule() + ".log").toFile());
    Process process = builder.start();
    children.put(nodeId, process);
    return Optional.empty();
  }

  static List<String> startCommand(Path moduleDir, Path jar, String module) {
    return startCommand(moduleDir, jar, module, null);
  }

  static List<String> startCommand(Path moduleDir, Path jar, String module, Path repoRoot) {
    List<String> command = new ArrayList<>();
    Path java = javaExecutable(repoRoot);
    command.add(java.toString());
    LocalConfigFile.Form form = loadTls(repoRoot);
    LocalConfigFile.ProcessRuntime runtime = LocalConfigFile.runtimeFor(form, module);
    command.add("-Xmx" + LocalConfigFile.clampHeap(runtime.heapMb, LocalConfigFile.DEFAULT_HEAP_MB) + "m");
    command.add("-Dspring.profiles.active=oss,local," + LocalConfigFile.nodeProfile(module));
    command.add("-Dserver.port=" + LocalConfigFile.clampPort(runtime.port, runtime.port));
    command.add("-Didserver.db.pool-size=" + LocalConfigFile.clampPool(runtime.dbPoolSize));
    // Force issuer on the command line so STS token iss and Admin OIDC validation stay aligned
    // (YAML import / local-profile precedence otherwise drifts — invalid_id_token / iss).
    String issuer = LocalConfigFile.canonicalizeIssuerUri(form.issuerUri);
    command.add("-Didserver.issuer-uri=" + issuer);
    // Force Admin sign-in keys over application-local.yml (e.g. role mismatch vs demo MyRole).
    if ("skoruba4j-admin".equals(module) || "skoruba4j-admin-api".equals(module)) {
      String role =
          form.adminRole == null || form.adminRole.isBlank()
              ? LocalConfigFile.DEFAULT_ADMIN_ROLE
              : form.adminRole.trim();
      command.add("-Didserver.admin.role=" + role);
      if ("skoruba4j-admin".equals(module)) {
        String loginMode = LocalConfigFile.canonicalizeLoginMode(form.loginMode);
        command.add("-Didserver.admin.login-mode=" + loginMode);
        command.add("-Didserver.admin.oidc-enabled=" + "sts-oidc".equals(loginMode));
        String clientId =
            form.clientId == null || form.clientId.isBlank()
                ? LocalConfigFile.DEFAULT_CLIENT_ID
                : form.clientId.trim();
        command.add("-Didserver.admin.client-id=" + clientId);
      }
    }
    if (repoRoot != null) {
      Path configDir = repoRoot.resolve("config").toAbsolutePath();
      Path overlay = configDir.resolve("idserver-local.yml");
      command.add("-Dspring.config.import=optional:file:" + overlay);
      command.add("-Dspring.config.additional-location=optional:file:" + overlay);
      command.add("-DIDSERVER_HOME=" + repoRoot.toAbsolutePath());
    }
    applyTlsJvmArgs(command, moduleDir, repoRoot, javaHomeOf(java), runtime);
    command.add("-jar");
    command.add(jar.toAbsolutePath().toString());
    return command;
  }

  static void applyTlsJvmArgs(
      List<String> command,
      Path moduleDir,
      Path repoRoot,
      Path javaHome,
      LocalConfigFile.ProcessRuntime runtime) {
    LocalConfigFile.Form ssl = loadTls(repoRoot);
    boolean https = runtime == null ? ssl.sslEnabled : runtime.sslEnabled;
    if (https && ssl.keyStore != null && !ssl.keyStore.isBlank()) {
      command.add("-Dserver.ssl.enabled=true");
      command.add(
          "-Dserver.ssl.key-store="
              + ssl.keyStore.replace("${user.dir}", moduleDir.toAbsolutePath().toString()));
      if (ssl.keyStorePassword != null && !ssl.keyStorePassword.isBlank()) {
        command.add("-Dserver.ssl.key-store-password=" + ssl.keyStorePassword);
      }
      command.add(
          "-Dserver.ssl.key-store-type=" + LocalConfigFile.canonicalizeStoreType(ssl.keyStoreType));
      if (ssl.keyAlias != null && !ssl.keyAlias.isBlank()) {
        command.add("-Dserver.ssl.key-alias=" + ssl.keyAlias);
      }
      Path cacerts = javaHome.resolve("lib").resolve("security").resolve("cacerts");
      if (Files.isRegularFile(cacerts)) {
        command.add("-Dserver.ssl.trust-store=" + cacerts.toAbsolutePath());
        command.add("-Dserver.ssl.trust-store-password=changeit");
      }
    } else if (!https) {
      command.add("-Dserver.ssl.enabled=false");
    }
  }

  static Path javaExecutable(Path home) {
    if (home != null) {
      Path windows = home.resolve("jdk").resolve("bin").resolve("java.exe");
      if (Files.isRegularFile(windows)) {
        return windows.toAbsolutePath();
      }
      Path unix = home.resolve("jdk").resolve("bin").resolve("java");
      if (Files.isRegularFile(unix)) {
        return unix.toAbsolutePath();
      }
    }
    String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", exe);
  }

  static Path javaHomeOf(Path javaExecutable) {
    if (javaExecutable == null) {
      return Path.of(System.getProperty("java.home"));
    }
    Path bin = javaExecutable.toAbsolutePath().getParent();
    return bin == null ? Path.of(System.getProperty("java.home")) : bin.getParent();
  }

  static Path findRunnableJar(Path home, String module) throws IOException {
    if (home == null) {
      return null;
    }
    Path fromLib = findJar(home.resolve("lib"), module);
    if (fromLib != null) {
      return fromLib;
    }
    return findJar(home.resolve(module).resolve("target"), module);
  }

  static Path workDir(Path home, String module) {
    if (home == null) {
      return Path.of(".");
    }
    if (RepoRoot.isInstallHome(home) || Files.isDirectory(home.resolve("lib"))) {
      return home.resolve("tomcat").resolve(module);
    }
    return home.resolve(module);
  }

  static LocalConfigFile.Form loadTls(Path repoRoot) {
    if (repoRoot == null) {
      return new LocalConfigFile.Form();
    }
    try {
      String overlay = LocalConfigFile.read(LocalConfigFile.resolve(repoRoot));
      String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
      return LocalConfigFile.load(overlay, privateYaml);
    } catch (Exception ignored) {
      return new LocalConfigFile.Form();
    }
  }

  public Optional<String> stop(String nodeId, ConsoleProperties.Node node) {
    if (node == null || !node.isLocal()) {
      return Optional.of("Remote nodes cannot be stopped from this console.");
    }
    Set<Long> pids = new LinkedHashSet<>();
    Process tracked = children.remove(nodeId);
    if (tracked != null && tracked.isAlive()) {
      pids.add(tracked.pid());
      destroy(tracked.toHandle());
    }
    pids.addAll(findPids(node));
    int stopped = 0;
    for (long pid : pids) {
      if (pid == ProcessHandle.current().pid()) {
        continue;
      }
      Optional<ProcessHandle> handle = ProcessHandle.of(pid);
      if (handle.isPresent() && handle.get().isAlive()) {
        destroy(handle.get());
        stopped++;
      }
    }
    if (stopped == 0 && pids.isEmpty()) {
      return Optional.of("No local process found listening on that port.");
    }
    return Optional.empty();
  }

  static Set<Long> findPids(ConsoleProperties.Node node) {
    Set<Long> pids = new LinkedHashSet<>();
    if (node == null) {
      return pids;
    }
    int port = LocalProcessLookup.portOf(node.getHealthUrl());
    try {
      pids.addAll(LocalProcessLookup.parseNetstatListeningPids(LocalProcessLookup.netstatOutput(), port));
    } catch (Exception ignored) {
      // fall through to command-line scan
    }
    long self = ProcessHandle.current().pid();
    ProcessHandle.allProcesses()
        .filter(ProcessHandle::isAlive)
        .filter(h -> h.pid() != self)
        .forEach(
            h -> {
              String cmd = h.info().commandLine().orElse("");
              if (LocalProcessLookup.commandLineMatches(cmd, node.getModule())) {
                pids.add(h.pid());
              }
            });
    pids.remove(self);
    return pids;
  }

  static void destroy(ProcessHandle handle) {
    if (handle == null || !handle.isAlive()) {
      return;
    }
    handle.destroy();
    try {
      handle.onExit().orTimeout(3, java.util.concurrent.TimeUnit.SECONDS).join();
    } catch (Exception ignored) {
      handle.destroyForcibly();
    }
    if (handle.isAlive()) {
      handle.destroyForcibly();
    }
  }

  public boolean startedHere(String nodeId) {
    Process process = children.get(nodeId);
    return process != null && process.isAlive();
  }

  /** True when this console tracks the process or a matching local listener/jar is alive. */
  public boolean processAlive(String nodeId, ConsoleProperties.Node node) {
    if (startedHere(nodeId)) {
      return true;
    }
    return node != null && node.isLocal() && !findPids(node).isEmpty();
  }

  static Path findJar(Path targetDir, String module) throws IOException {
    if (targetDir == null || !Files.isDirectory(targetDir) || module == null) {
      return null;
    }
    try (Stream<Path> stream = Files.list(targetDir)) {
      return stream
          .filter(p -> p.getFileName().toString().startsWith(module + "-"))
          .filter(p -> p.getFileName().toString().endsWith(".jar"))
          .filter(p -> !p.getFileName().toString().contains("sources"))
          .filter(p -> !p.getFileName().toString().contains("javadoc"))
          .filter(p -> !p.getFileName().toString().contains("original"))
          .findFirst()
          .orElse(null);
    }
  }
}
