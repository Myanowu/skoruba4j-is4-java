package com.myano.skoruba4j.console.jdbc;

import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.configstore.ClientSummary;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import javax.sql.DataSource;

/** Loads enabled IS4 {@code Clients} for the console Admin client-id dropdown. */
public final class JdbcClientChoices {
  public record Item(String clientId, String clientName, boolean adminUi) {
    @Override
    public String toString() {
      String id = clientId == null ? "" : clientId;
      String name = clientName == null ? "" : clientName.trim();
      String label = name.isEmpty() || name.equals(id) ? id : id + " — " + name;
      return adminUi ? label + "  (Admin UI)" : label;
    }
  }

  public record Result(List<Item> items, List<String> roles, String error) {
    public static Result ok(List<Item> items, List<String> roles) {
      return new Result(List.copyOf(items), List.copyOf(roles), "");
    }

    public static Result fail(String error) {
      return new Result(List.of(), List.of(), error == null ? "" : error);
    }
  }

  private JdbcClientChoices() {}

  public static DataSource open(LocalConfigFile.Form form, Path installHome) throws Exception {
    if (form == null) {
      throw new IllegalArgumentException("No JDBC settings.");
    }
    String rawUrl = form.url == null ? "" : form.url.trim();
    if (rawUrl.isBlank()) {
      throw new IllegalArgumentException("Set the JDBC URL first.");
    }
    DbProvider provider = DbProvider.fromConfig(form.provider);
    loadDriver(provider);
    String jdbcUrl = JdbcUrlComposer.normalize(form.provider, rawUrl, installHome);
    return new DriverUrlDataSource(jdbcUrl, form.username, form.password);
  }

  public static DataSource openUrl(DbProvider provider, String url, String username, String password)
      throws Exception {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("Set the JDBC URL first.");
    }
    loadDriver(provider);
    String jdbcUrl =
        JdbcUrlComposer.normalize(provider == null ? null : provider.name(), url, null);
    return new DriverUrlDataSource(jdbcUrl, username, password);
  }

  public static Result load(LocalConfigFile.Form form, Path installHome) {
    if (form == null) {
      return Result.fail("No JDBC settings.");
    }
    String rawUrl = form.url == null ? "" : form.url.trim();
    if (rawUrl.isBlank()) {
      return Result.fail("Set the JDBC URL, then load Clients from the database.");
    }
    try {
      DbProvider provider = DbProvider.fromConfig(form.provider);
      TableStyle style = TableStyle.fromConfig(form.tableStyle);
      JdbcRepositories repos = new JdbcRepositories(open(form, installHome), provider, style);
      List<ClientSummary> rows = repos.clients().listEnabled(500);
      Set<String> adminUi = new HashSet<>(repos.clients().listEnabledAdminUiClientIds());
      List<Item> items = new ArrayList<>();
      for (ClientSummary row : rows) {
        if (row.clientId() == null || row.clientId().isBlank()) {
          continue;
        }
        items.add(new Item(row.clientId(), row.clientName(), adminUi.contains(row.clientId())));
      }
      items.sort(
          Comparator.comparing(Item::adminUi)
              .reversed()
              .thenComparing(item -> item.clientId(), String.CASE_INSENSITIVE_ORDER));
      List<String> roles = repos.roles().listNames(500);
      roles.sort(String.CASE_INSENSITIVE_ORDER);
      if (items.isEmpty() && roles.isEmpty()) {
        return Result.fail("No enabled Clients or Roles in this database.");
      }
      return Result.ok(items, roles);
    } catch (Exception e) {
      String message = e.getMessage();
      if (message == null || message.isBlank()) {
        message = e.getClass().getSimpleName();
      }
      return Result.fail(message);
    }
  }

  public static Item preferred(List<Item> items, String currentId) {
    if (items == null || items.isEmpty()) {
      return null;
    }
    String want = currentId == null ? "" : currentId.trim();
    if (!want.isBlank()) {
      for (Item item : items) {
        if (want.equals(item.clientId())) {
          return item;
        }
      }
    }
    for (Item item : items) {
      if (item.adminUi()) {
        return item;
      }
    }
    return items.get(0);
  }

  public static String preferredRole(List<String> roles, String current) {
    if (roles == null || roles.isEmpty()) {
      return null;
    }
    String want = current == null ? "" : current.trim();
    if (!want.isBlank()) {
      for (String role : roles) {
        if (want.equalsIgnoreCase(role)) {
          return role;
        }
      }
    }
    for (String role : roles) {
      if ("Admin".equalsIgnoreCase(role) || "Administrator".equalsIgnoreCase(role)) {
        return role;
      }
    }
    return roles.get(0);
  }

  public static void loadDriver(DbProvider provider) throws Exception {
    String className = provider.driverClassName();
    try {
      Class.forName(className);
      return;
    } catch (ClassNotFoundException ignored) {
      // ServiceLoader JDBC 4 drivers still work when the class is on the classpath.
    }
    for (Driver driver : ServiceLoader.load(Driver.class)) {
      if (className.equals(driver.getClass().getName())) {
        return;
      }
    }
  }
}
