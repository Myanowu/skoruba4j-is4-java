package com.myano.skoruba4j.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TableStyleTest {

  @Test
  void skorubaUsesUsersNotAspNetUsers() {
    IdentityTables tables = IdentityTables.forStyle(TableStyle.SKORUBA);
    assertEquals("Users", tables.users());
    assertEquals("Roles", tables.roles());
    assertEquals("UserRoles", tables.userRoles());
  }

  @Test
  void aspnetUsesAspNetPrefix() {
    IdentityTables tables = IdentityTables.forStyle(TableStyle.ASPNET);
    assertEquals("AspNetUsers", tables.users());
    assertEquals("AspNetRoles", tables.roles());
    assertEquals("AspNetUserRoles", tables.userRoles());
  }

  @Test
  void parsesConfigValues() {
    assertEquals(TableStyle.SKORUBA, TableStyle.fromConfig("skoruba"));
    assertEquals(TableStyle.ASPNET, TableStyle.fromConfig("aspnet"));
    assertEquals(TableStyle.SKORUBA, TableStyle.fromConfig(null));
    assertThrows(IllegalArgumentException.class, () -> TableStyle.fromConfig("hibernate"));
  }
}
