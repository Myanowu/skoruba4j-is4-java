package com.myano.skoruba4j.i18n;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Language links that post to GET /lang. */
public final class LangSwitcher {
  private LangSwitcher() {}

  public static String html() {
    return html(UiLocale.requestPath());
  }

  public static String html(String returnPath) {
    String ret = URLEncoder.encode(safeReturn(returnPath), StandardCharsets.UTF_8);
    StringBuilder html = new StringBuilder("<nav class=\"langs\" aria-label=\"");
    html.append(esc(Messages.t("lang.choose"))).append("\">");
    for (UiLocale locale : UiLocale.values()) {
      boolean current = locale == UiLocale.current();
      html.append("<a");
      if (current) {
        html.append(" class=\"on\" aria-current=\"true\"");
      }
      html.append(" href=\"/lang?code=")
          .append(locale.code())
          .append("&amp;return=")
          .append(esc(ret))
          .append("\">")
          .append(esc(locale.label()))
          .append("</a>");
    }
    html.append("</nav>");
    return html.toString();
  }

  public static String safeReturn(String path) {
    if (path == null || path.isBlank() || !path.startsWith("/") || path.startsWith("//")) {
      return "/";
    }
    return path;
  }

  private static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
