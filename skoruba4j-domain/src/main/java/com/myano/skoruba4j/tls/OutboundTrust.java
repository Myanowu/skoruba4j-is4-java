package com.myano.skoruba4j.tls;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Trust material for outbound HTTPS (Admin/Admin API calling STS). Never replace the JVM-wide
 * {@code javax.net.ssl.trustStore}: Tomcat's HTTPS listener uses that store as trust anchors, and a
 * server PKCS12 (PrivateKeyEntry only) makes {@code trustAnchors} empty if used alone.
 *
 * <p>Homemade install certs are not in Windows ROOT / cacerts. Leaf certificates from the HTTPS
 * key store are merged in so {@code /connect/token} can complete.
 */
public final class OutboundTrust {
  private OutboundTrust() {}

  public static SSLContext sslContext(String location, String password, String type) {
    return sslContext(location, password, type, null, null, null);
  }

  public static SSLContext sslContext(
      String location,
      String password,
      String type,
      String extraLocation,
      String extraPassword,
      String extraType) {
    try {
      KeyStore combined = trustMaterial(location, password, type, extraLocation, extraPassword, extraType);
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      if (hasTrustAnchors(combined)) {
        tmf.init(combined);
      } else {
        tmf.init((KeyStore) null);
      }
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmf.getTrustManagers(), null);
      return context;
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("outbound HTTPS trust", ex);
    }
  }

  static KeyStore trustMaterial(
      String location,
      String password,
      String type,
      String extraLocation,
      String extraPassword,
      String extraType)
      throws Exception {
    KeyStore combined = KeyStore.getInstance("PKCS12");
    combined.load(null, null);
    copyCertificates(combined, platformTrustStore(), "platform");
    copyCertificates(combined, loadStore(location, password, type), "trust");
    copyCertificates(combined, loadStore(extraLocation, extraPassword, extraType), "https");
    return combined;
  }

  static KeyStore loadStore(String location, String password, String type) throws Exception {
    Path path = filesystemPath(location);
    if (path == null || !Files.isRegularFile(path)) {
      return null;
    }
    KeyStore store = KeyStore.getInstance(storeType(type, path));
    char[] chars = password == null || password.isBlank() ? new char[0] : password.toCharArray();
    try (InputStream in = Files.newInputStream(path)) {
      store.load(in, chars);
    }
    return store;
  }

  static KeyStore platformTrustStore() throws Exception {
    if (isWindows()) {
      try {
        KeyStore windows = KeyStore.getInstance("Windows-ROOT");
        windows.load(null, null);
        if (hasTrustAnchors(windows)) {
          return windows;
        }
      } catch (Exception ignored) {
        // fall through to JVM cacerts
      }
    }
    Path cacerts =
        Path.of(System.getProperty("java.home", ""), "lib", "security", "cacerts");
    if (Files.isRegularFile(cacerts)) {
      try {
        return loadStore(cacerts.toString(), "changeit", "JKS");
      } catch (Exception ignored) {
        return loadStore(cacerts.toString(), "", "JKS");
      }
    }
    return null;
  }

  static int copyCertificates(KeyStore dest, KeyStore source, String prefix) throws Exception {
    if (dest == null || source == null) {
      return 0;
    }
    int copied = 0;
    Enumeration<String> aliases = source.aliases();
    while (aliases.hasMoreElements()) {
      String alias = aliases.nextElement();
      Certificate[] chain = source.getCertificateChain(alias);
      if (chain != null && chain.length > 0) {
        for (int i = 0; i < chain.length; i++) {
          if (chain[i] != null) {
            dest.setCertificateEntry(prefix + "-" + alias + "-" + i, chain[i]);
            copied++;
          }
        }
        continue;
      }
      Certificate cert = source.getCertificate(alias);
      if (cert != null) {
        dest.setCertificateEntry(prefix + "-" + alias, cert);
        copied++;
      }
    }
    return copied;
  }

  static Path filesystemPath(String location) {
    if (location == null || location.isBlank()) {
      return null;
    }
    String value = location.trim();
    if (value.regionMatches(true, 0, "file:", 0, 5)) {
      value = value.substring(5);
    }
    value = value.replace("${user.dir}", System.getProperty("user.dir", ""));
    value = value.replace("${java.home}", System.getProperty("java.home", ""));
    return Path.of(value).toAbsolutePath().normalize();
  }

  static String storeType(String type, Path path) {
    if (type != null && !type.isBlank()) {
      return type.trim();
    }
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".jks")) {
      return "JKS";
    }
    return "PKCS12";
  }

  static boolean hasTrustAnchors(KeyStore store) throws Exception {
    if (store == null) {
      return false;
    }
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(store);
    for (TrustManager manager : tmf.getTrustManagers()) {
      if (manager instanceof X509TrustManager x509 && x509.getAcceptedIssuers().length > 0) {
        return true;
      }
    }
    return false;
  }

  static boolean isWindows() {
    String os = System.getProperty("os.name", "");
    return os.toLowerCase(Locale.ROOT).contains("win");
  }
}
