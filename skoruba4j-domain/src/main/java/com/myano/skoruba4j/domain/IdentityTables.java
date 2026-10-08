package com.myano.skoruba4j.domain;

/** Identity table names for {@code table-style=skoruba|aspnet}. */
public record IdentityTables(
    String users,
    String roles,
    String userRoles,
    String userClaims,
    String roleClaims,
    String userLogins,
    String userTokens) {

  public static IdentityTables forStyle(TableStyle style) {
    return switch (style) {
      case SKORUBA ->
          new IdentityTables(
              "Users",
              "Roles",
              "UserRoles",
              "UserClaims",
              "RoleClaims",
              "UserLogins",
              "UserTokens");
      case ASPNET ->
          new IdentityTables(
              "AspNetUsers",
              "AspNetRoles",
              "AspNetUserRoles",
              "AspNetUserClaims",
              "AspNetRoleClaims",
              "AspNetUserLogins",
              "AspNetUserTokens");
    };
  }
}
