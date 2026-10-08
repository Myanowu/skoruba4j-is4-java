package com.myano.skoruba4j.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PageQueryTest {

  @Test
  void defaultsAndLikeStripWildcards() {
    PageQuery query = PageQuery.of("  a%b_c  ", null, 500);
    assertEquals(1, query.page());
    assertEquals(100, query.pageSize());
    assertEquals(0, query.offset());
    assertTrue(query.hasSearch());
    assertEquals("%abc%", query.likeContains());
    assertFalse(PageQuery.of("  ", 2, 10).hasSearch());
    assertEquals(10, PageQuery.of(null, 2, 10).offset());
  }
}
