package com.myano.skoruba4j.domain.configstore;

/** Filters for Skoruba Admin Audit Log search (same fields as C# AuditLogFilterDto). */
public record AuditLogFilter(
    String subjectIdentifier,
    String subjectName,
    String event,
    String source,
    String category,
    int page,
    int pageSize) {

  public static AuditLogFilter of(
      String subjectIdentifier,
      String subjectName,
      String event,
      String source,
      String category,
      Integer page,
      Integer pageSize) {
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
    return new AuditLogFilter(
        blankToNull(subjectIdentifier),
        blankToNull(subjectName),
        blankToNull(event),
        blankToNull(source),
        blankToNull(category),
        p,
        size);
  }

  public int offset() {
    return (page - 1) * pageSize;
  }

  public boolean hasAny() {
    return subjectIdentifier != null
        || subjectName != null
        || event != null
        || source != null
        || category != null;
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
