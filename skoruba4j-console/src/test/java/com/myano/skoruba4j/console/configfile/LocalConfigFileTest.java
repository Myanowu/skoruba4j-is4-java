package com.myano.skoruba4j.console.configfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalConfigFileTest {

  @Test
  void keepsPreviousPasswordWhenFormPasswordBlank() {
    LocalConfigFile.Form first = baseForm();
    first.password = "secret";
    first.keyStorePassword = "changeit";
    first.clientSecret = "keep-secret";
    first.smtpHost = "smtp.example.test";
    first.smtpPort = 587;
    first.smtpUsername = "mailer";
    first.smtpPassword = "smtp-pass";
    first.smtpFrom = "noreply@example.test";
    first.smtpResetKey = "reset-keep";
    String previous = LocalConfigFile.write(first, "");
    LocalConfigFile.Form next = baseForm();
    next.password = "";
    next.keyStorePassword = "";
    next.clientSecret = "";
    next.smtpPassword = "";
    next.smtpResetKey = "";
    next.loginMode = "sts-oidc";
    next.oidcEnabled = true;
    String yaml = LocalConfigFile.write(next, previous);
    assertEquals("secret", LocalConfigFile.yamlValue(previous, "password"));
    assertEquals("sts-oidc", LocalConfigFile.yamlValue(yaml, "login-mode"));
    assertTrue(yaml.contains("oidc-enabled: true"));
    assertEquals("secret", LocalConfigFile.yamlValue(yaml, "password"));
    assertEquals("changeit", LocalConfigFile.yamlValue(yaml, "key-store-password"));
    assertEquals("keep-secret", LocalConfigFile.nestedYamlValue(yaml, "admin", "client-secret"));
    assertEquals("smtp-pass", LocalConfigFile.nestedYamlValue(yaml, "smtp", "password"));
    assertEquals("reset-keep", LocalConfigFile.nestedYamlValue(yaml, "smtp", "reset-key"));
  }

  @Test
  void blankFieldsGetDocumentedDefaults() {
    String yaml = LocalConfigFile.write(new LocalConfigFile.Form(), "");
    assertEquals(LocalConfigFile.DEFAULT_PROVIDER, LocalConfigFile.yamlValue(yaml, "provider"));
    assertEquals(LocalConfigFile.EMBEDDED_JDBC_URL, LocalConfigFile.yamlValue(yaml, "url"));
    assertEquals(LocalConfigFile.DEFAULT_TABLE_STYLE, LocalConfigFile.yamlValue(yaml, "table-style"));
    assertEquals(LocalConfigFile.DEFAULT_ISSUER_URI, LocalConfigFile.yamlValue(yaml, "issuer-uri"));
    assertEquals(LocalConfigFile.DEFAULT_END_SESSION, LocalConfigFile.yamlValue(yaml, "end-session"));
    assertEquals(
        LocalConfigFile.DEFAULT_CLIENT_ID, LocalConfigFile.nestedYamlValue(yaml, "admin", "client-id"));
    assertEquals(
        LocalConfigFile.DEFAULT_ADMIN_ROLE, LocalConfigFile.nestedYamlValue(yaml, "admin", "role"));
    assertEquals(LocalConfigFile.DEFAULT_LOGIN_MODE, LocalConfigFile.yamlValue(yaml, "login-mode"));
    assertEquals("false", LocalConfigFile.yamlValue(yaml, "oidc-enabled"));
    assertEquals("true", LocalConfigFile.yamlValue(yaml, "api-ui-enabled"));
    assertEquals(
        LocalConfigFile.DEFAULT_API_LOGIN_MODE, LocalConfigFile.yamlValue(yaml, "api-login-mode"));
    assertEquals(
        LocalConfigFile.DEFAULT_ADMIN_API_CLIENT_ID,
        LocalConfigFile.yamlValue(yaml, "api-client-id"));
    assertEquals(LocalConfigFile.DEFAULT_KEY_STORE, LocalConfigFile.yamlValue(yaml, "key-store"));
    assertEquals(LocalConfigFile.DEFAULT_KEY_STORE_TYPE, LocalConfigFile.yamlValue(yaml, "key-store-type"));
    assertEquals(LocalConfigFile.DEFAULT_KEY_ALIAS, LocalConfigFile.yamlValue(yaml, "key-alias"));
    assertTrue(yaml.contains("server:"));
    assertTrue(yaml.contains("ssl:"));
    assertTrue(yaml.contains("tls:"));
    assertEquals("5051", LocalConfigFile.yamlValue(yaml, "sts-port"));
    assertEquals("true", LocalConfigFile.yamlValue(yaml, "sts-ssl-enabled"));
    assertEquals("512", LocalConfigFile.yamlValue(yaml, "sts-heap-mb"));
    assertEquals("4", LocalConfigFile.yamlValue(yaml, "sts-db-pool-size"));
    assertEquals("6061", LocalConfigFile.yamlValue(yaml, "admin-port"));
    assertEquals("44302", LocalConfigFile.yamlValue(yaml, "admin-api-port"));
    assertTrue(yaml.contains("on-profile: node-sts"));
    assertTrue(yaml.contains("smtp:"));
    assertEquals("587", LocalConfigFile.nestedYamlValue(yaml, "smtp", "port"));
    assertEquals("true", LocalConfigFile.nestedYamlValue(yaml, "smtp", "start-tls"));
  }

  @Test
  void providerAliasesMapToDropdownValues() {
    assertEquals("sqlserver", LocalConfigFile.canonicalizeProvider("mssql"));
    assertEquals("sqlserver", LocalConfigFile.canonicalizeProvider("sqlserver"));
    assertEquals("sqlite", LocalConfigFile.canonicalizeProvider(""));
    assertEquals("postgresql", LocalConfigFile.canonicalizeProvider("postgres"));
    assertEquals("mysql", LocalConfigFile.canonicalizeProvider("MYSQL"));
    assertEquals("sqlite", LocalConfigFile.canonicalizeProvider("SQLITE"));
  }

  @Test
  void embeddedSqliteUrlIsRelativeToInstallHome() {
    String url = LocalConfigFile.embeddedJdbcUrl(Path.of("C:/install"));
    assertEquals(LocalConfigFile.EMBEDDED_JDBC_URL, url);
    assertTrue(url.contains("${IDSERVER_HOME}/data/skoruba4j.sqlite"));
    assertTrue(url.contains("journal_mode=WAL"));
    assertFalse(url.contains("C:/install"));
  }

  @Test
  void issuerUriPrefersLocalhostOverLoopbackIp() {
    assertEquals(
        "https://localhost:5051",
        LocalConfigFile.canonicalizeIssuerUri("https://127.0.0.1:5051/"));
    LocalConfigFile.Form form =
        LocalConfigFile.load("issuer-uri: 'https://127.0.0.1:5051'\n", "");
    assertEquals("https://localhost:5051", form.issuerUri);
    String yaml = LocalConfigFile.write(form, "");
    assertEquals("https://localhost:5051", LocalConfigFile.yamlValue(yaml, "issuer-uri"));
  }

  @Test
  void loginModePrefersExplicitKeyAndMapsLegacyOidc() {
    LocalConfigFile.Form blank =
        LocalConfigFile.load("issuer-uri: 'https://localhost:5051'\n", "");
    assertEquals(LocalConfigFile.DEFAULT_LOGIN_MODE, blank.loginMode);
    assertFalse(blank.oidcEnabled);

    LocalConfigFile.Form oidcOn = LocalConfigFile.load("oidc-enabled: true\n", "");
    assertEquals("sts-oidc", oidcOn.loginMode);
    assertTrue(oidcOn.oidcEnabled);

    LocalConfigFile.Form oidcOff = LocalConfigFile.load("oidc-enabled: false\n", "");
    assertEquals("local", oidcOff.loginMode);
    assertFalse(oidcOff.oidcEnabled);

    LocalConfigFile.Form password =
        LocalConfigFile.load("login-mode: sts-password\noidc-enabled: true\n", "");
    assertEquals("sts-password", password.loginMode);
    assertFalse(password.oidcEnabled);
  }

  @Test
  void overlayDemoClientIdDoesNotHideCopiedAdminClient() {
    String overlay = "client-id: 'skoruba4j-admin'\nrole: 'MyRole'\n";
    String privateYaml = "client-id: 'existing-admin'\nrole: 'Admin'\n";
    LocalConfigFile.Form form = LocalConfigFile.load(overlay, privateYaml);
    assertEquals("existing-admin", form.clientId);
    assertEquals("Admin", form.adminRole);
  }

  @Test
  void writesPerProcessListenAndHeap() {
    LocalConfigFile.Form form = baseForm();
    form.sts.port = 5051;
    form.sts.sslEnabled = true;
    form.sts.heapMb = 768;
    form.sts.dbPoolSize = 3;
    form.adminProc.port = 7071;
    form.adminProc.sslEnabled = false;
    String yaml = LocalConfigFile.write(form, "");
    LocalConfigFile.Form loaded = LocalConfigFile.load(yaml, "");
    assertEquals(5051, loaded.sts.port);
    assertEquals(768, loaded.sts.heapMb);
    assertEquals(3, loaded.sts.dbPoolSize);
    assertEquals(7071, loaded.adminProc.port);
    assertFalse(loaded.adminProc.sslEnabled);
    assertTrue(LocalConfigFile.jvmEnvCmd(form).contains("IDSERVER_STS_XMX=768m"));
  }

  @Test
  void overlayWinsOverPrivateProfileForJdbc() {
    String overlay = "url: 'jdbc:overlay'\nusername: 'local-user'\n";
    String privateYaml = "url: 'jdbc:private'\nusername: 'private-user'\npassword: 'private-secret'\n";
    assertEquals("jdbc:overlay", LocalConfigFile.firstYamlValue("url", overlay, privateYaml));
    assertEquals("private-secret", LocalConfigFile.firstYamlValue("password", overlay, privateYaml));
  }

  @Test
  void filesystemPathExpandsUserDirAndFilePrefix() {
    Path module = Path.of("").toAbsolutePath().resolve("skoruba4j-sts");
    Path expected = module.resolve("local-ssl.p12").normalize();
    Path actual =
        Path.of(LocalConfigFile.filesystemPath("file:${user.dir}/local-ssl.p12", module)).normalize();
    assertEquals(expected, actual);
    assertFalse(LocalConfigFile.springFileLocation(expected).contains("\\"));
  }

  @Test
  void endSessionIsOnlyCompatibleOrStrict() {
    assertEquals("strict", LocalConfigFile.canonicalizeEndSession("STRICT"));
    assertEquals("compatible", LocalConfigFile.canonicalizeEndSession("nope"));
    LocalConfigFile.Form form = baseForm();
    form.endSession = "STRICT";
    assertEquals("strict", LocalConfigFile.yamlValue(LocalConfigFile.write(form, ""), "end-session"));
  }

  @Test
  void accountChooserDefaultsOnAndWritesUnderLogin() {
    LocalConfigFile.Form form = baseForm();
    assertTrue(form.accountChooserEnabled);
    form.accountChooserEnabled = false;
    String yaml = LocalConfigFile.write(form, "");
    assertTrue(yaml.contains("login:"));
    assertEquals("false", LocalConfigFile.yamlValue(yaml, "account-chooser"));
    LocalConfigFile.Form loaded = LocalConfigFile.load(yaml, "");
    assertFalse(loaded.accountChooserEnabled);
    assertTrue(LocalConfigFile.load("", "").accountChooserEnabled);
  }

  @Test
  void registerFlagsDefaultOffAndOnAndRoundTrip() {
    LocalConfigFile.Form defaults = LocalConfigFile.load("", "");
    assertFalse(defaults.allowRegister);
    assertTrue(defaults.registerCaptcha);
    LocalConfigFile.Form form = baseForm();
    form.allowRegister = true;
    form.registerCaptcha = false;
    String yaml = LocalConfigFile.write(form, "");
    assertEquals("true", LocalConfigFile.nestedYamlValue(yaml, "login", "allow-register"));
    assertEquals("false", LocalConfigFile.nestedYamlValue(yaml, "login", "register-captcha"));
    LocalConfigFile.Form loaded = LocalConfigFile.load(yaml, "");
    assertTrue(loaded.allowRegister);
    assertFalse(loaded.registerCaptcha);
  }

  @Test
  void debugLoginWritesUnderLoginAndKeepsPasswordWhenBlank() {
    LocalConfigFile.Form form = baseForm();
    form.debugMode = true;
    form.debugPassword = "temp-debug";
    String yaml = LocalConfigFile.write(form, "");
    assertEquals("true", LocalConfigFile.nestedYamlValue(yaml, "login", "debug-mode"));
    assertEquals("temp-debug", LocalConfigFile.nestedYamlValue(yaml, "login", "debug-password"));
    LocalConfigFile.Form again = LocalConfigFile.load(yaml, "");
    again.debugPassword = "";
    String kept = LocalConfigFile.write(again, yaml);
    assertEquals("temp-debug", LocalConfigFile.nestedYamlValue(kept, "login", "debug-password"));
    assertTrue(LocalConfigFile.load(kept, "").debugMode);
    assertFalse(LocalConfigFile.load("", "").debugMode);
  }

  @Test
  void smtpBlockDoesNotStealDbUsername() {
    LocalConfigFile.Form form = baseForm();
    form.username = "sa";
    form.smtpUsername = "mailer";
    form.smtpHost = "smtp.example.test";
    form.smtpPort = 2525;
    form.smtpStartTls = false;
    String yaml = LocalConfigFile.write(form, "");
    LocalConfigFile.Form loaded = LocalConfigFile.load(yaml, "");
    assertEquals("sa", loaded.username);
    assertEquals("mailer", loaded.smtpUsername);
    assertEquals("smtp.example.test", loaded.smtpHost);
    assertEquals(2525, loaded.smtpPort);
    assertFalse(loaded.smtpStartTls);
  }

  private static LocalConfigFile.Form baseForm() {
    LocalConfigFile.Form form = new LocalConfigFile.Form();
    form.provider = "sqlserver";
    form.url = "jdbc:sqlserver://127.0.0.1";
    form.username = "sa";
    form.tableStyle = "skoruba";
    form.issuerUri = "http://127.0.0.1:5050";
    form.endSession = "compatible";
    form.clientId = "skoruba4j-admin";
    form.adminRole = "MyRole";
    return form;
  }
}
