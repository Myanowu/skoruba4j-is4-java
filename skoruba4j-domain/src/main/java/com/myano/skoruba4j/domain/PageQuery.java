package com.myano.skoruba4j.domain;

public record PageQuery(String searchText, int page, int pageSize) {
  public static PageQuery of(String searchText, Integer page, Integer pageSize) {
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    String text = searchText == null ? "" : searchText.trim();
    return new PageQuery(text, p, size);
  }

  public int offset() {
    return (page - 1) * pageSize;
  }

  public boolean hasSearch() {
    return searchText != null && !searchText.isBlank();
  }

  public String likeContains() {
    String stripped = searchText.replace("%", "").replace("_", "");
    return "%" + stripped + "%";
  }
}
