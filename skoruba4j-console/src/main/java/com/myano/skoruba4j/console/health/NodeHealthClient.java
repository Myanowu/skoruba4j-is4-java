package com.myano.skoruba4j.console.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Fetches `/health`. Loopback HTTPS may use a local dev cert not in cacerts. */
public final class NodeHealthClient {
  private static final Pattern JSON_STRING =
      Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

  private NodeHealthClient() {}

  public record Summary(boolean up, String status, String database, String process, String detail) {
    public String line() {
      if (!up) {
        return detail == null || detail.isBlank() ? "DOWN" : detail;
      }
      StringBuilder sb = new StringBuilder(status == null || status.isBlank() ? "UP" : status);
      if (database != null && !database.isBlank()) {
        sb.append(" · db ").append(database);
      }
      if (process != null && !process.isBlank()) {
        sb.append(" · ").append(process);
      }
      return sb.toString();
    }
  }

  public static String fetch(String healthUrl) {
    if (healthUrl == null || healthUrl.isBlank()) {
      return "down: missing URL";
    }
    try {
      URI uri = URI.create(healthUrl.trim());
      HttpClient client = httpClient(uri);
      HttpRequest request =
          HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(3)).build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return response.body();
      }
      return "down: HTTP " + response.statusCode();
    } catch (Exception e) {
      return "down: " + e.getClass().getSimpleName();
    }
  }

  public static Summary summarize(String body) {
    if (body == null || body.isBlank()) {
      return new Summary(false, "DOWN", "", "", "no response");
    }
    String trimmed = body.trim();
    if (trimmed.regionMatches(true, 0, "down:", 0, 5)) {
      return new Summary(false, "DOWN", "", "", trimmed);
    }
    String status = jsonString(trimmed, "status");
    String database = jsonString(trimmed, "database");
    String process = jsonString(trimmed, "process");
    boolean up = status != null && "UP".equalsIgnoreCase(status);
    if (!up && trimmed.toUpperCase(Locale.ROOT).contains("\"STATUS\"") && trimmed.toUpperCase(Locale.ROOT).contains("UP")) {
      // defensive for oddly cased payloads
      up = true;
      if (status == null || status.isBlank()) {
        status = "UP";
      }
    }
    if (!up) {
      String why =
          database != null && database.toUpperCase(Locale.ROOT).contains("DOWN")
              ? "database DOWN"
              : (status == null || status.isBlank() ? "DOWN" : status);
      return new Summary(false, "DOWN", database == null ? "" : database, process == null ? "" : process, why);
    }
    if (database != null && database.toUpperCase(Locale.ROOT).contains("DOWN")) {
      return new Summary(
          false, "UP", database, process == null ? "" : process, "process UP · database DOWN");
    }
    return new Summary(
        true,
        status == null || status.isBlank() ? "UP" : status,
        database == null ? "" : database,
        process == null ? "" : process,
        "");
  }

  static String jsonString(String json, String key) {
    if (json == null || key == null) {
      return null;
    }
    Matcher m = JSON_STRING.matcher(json);
    while (m.find()) {
      if (key.equalsIgnoreCase(m.group(1))) {
        return m.group(2);
      }
    }
    return null;
  }

  public static boolean isLoopback(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      String host = URI.create(url.trim()).getHost();
      if (host == null) {
        return false;
      }
      String h = host.toLowerCase(Locale.ROOT);
      return "127.0.0.1".equals(h) || "localhost".equals(h) || "[::1]".equals(h) || "::1".equals(h);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static HttpClient httpClient(URI uri) throws Exception {
    HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3));
    if ("https".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.toString())) {
      SSLContext ssl = SSLContext.getInstance("TLS");
      ssl.init(null, new TrustManager[] {LOOPBACK_TRUST}, new SecureRandom());
      builder.sslContext(ssl);
      SSLParameters params = new SSLParameters();
      params.setEndpointIdentificationAlgorithm(null);
      builder.sslParameters(params);
    }
    return builder.build();
  }

  private static final X509TrustManager LOOPBACK_TRUST =
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };
}
