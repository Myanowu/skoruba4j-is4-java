package com.myano.skoruba4j.adminapi.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminDtosJsonTest {

  @Test
  void clientsAndUsersUseSkorubaCamelCase() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    AdminDtos.ClientsDto clients =
        new AdminDtos.ClientsDto(
            10,
            16,
            1,
            List.of(
                new AdminDtos.ClientDto(
                    1,
                    "demo-web",
                    "Demo",
                    true,
                    true,
                    true,
                    true,
                    3600,
                    86400,
                    List.of("authorization_code"),
                    List.of("openid"),
                    List.of(),
                    List.of(),
                    List.of())));
    String json = mapper.writeValueAsString(clients);
    assertTrue(json.contains("\"pageSize\":10"));
    assertTrue(json.contains("\"totalCount\":16"));
    assertTrue(json.contains("\"clients\""));
    assertTrue(json.contains("\"clientId\":\"demo-web\""));
    assertTrue(json.contains("\"allowedGrantTypes\""));
    assertFalse(json.contains("passwordHash"));

    AdminDtos.UsersDto users =
        new AdminDtos.UsersDto(
            10,
            1,
            1,
            List.of(
                new AdminDtos.UserDto(
                    "id-1", "alice", "a@example.com", true, false, false, "", false, 0, null)));
    String userJson = mapper.writeValueAsString(users);
    assertTrue(userJson.contains("\"users\""));
    assertTrue(userJson.contains("\"userName\":\"alice\""));
    assertFalse(userJson.contains("passwordHash"));
  }

  @Test
  void rolesListAlwaysIncludesRolesArrayForSkorubaClient() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    String empty = mapper.writeValueAsString(new AdminDtos.RolesDto(1, 0, 1, List.of()));
    assertTrue(empty.contains("\"pageSize\":1"));
    assertTrue(empty.contains("\"totalCount\":0"));
    assertTrue(empty.contains("\"roles\":[]"));

    String found =
        mapper.writeValueAsString(
            new AdminDtos.RolesDto(
                1, 1, 1, List.of(new AdminDtos.RoleDto("role-id", "4SUser"))));
    assertTrue(found.contains("\"totalCount\":1"));
    assertTrue(found.contains("\"id\":\"role-id\""));
    assertTrue(found.contains("\"name\":\"4SUser\""));
  }

  @Test
  void userJsonNeverOmitsEmailOrUserName() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    String json =
        mapper.writeValueAsString(
            AdminDtos.fromUser(
                new com.myano.skoruba4j.domain.identity.IdentityUser(
                    "id-1",
                    "alice@example.com",
                    "ALICE@EXAMPLE.COM",
                    null,
                    null,
                    true,
                    "hash",
                    "stamp",
                    false,
                    null,
                    0,
                    false,
                    null,
                    false)));
    assertTrue(json.contains("\"userName\":\"alice@example.com\""));
    assertTrue(json.contains("\"email\":\"\""));
    assertTrue(json.contains("\"phoneNumberConfirmed\":false"));
    assertTrue(json.contains("\"accessFailedCount\":0"));
    assertFalse(json.contains("\"email\":null"));
    assertFalse(json.contains("lockoutEnd"));
  }

  @Test
  void userUpsertIgnoresSkorubaIdentityUserDtoFlags() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    AdminDtos.UserUpsert body =
        mapper.readValue(
            """
            {"userName":"a@b.com","email":"a@b.com","emailConfirmed":true,\
            "phoneNumber":"1","phoneNumberConfirmed":false,"lockoutEnabled":false,\
            "twoFactorEnabled":false,"accessFailedCount":0}
            """,
            AdminDtos.UserUpsert.class);
    assertTrue("a@b.com".equals(body.userName()));
    assertTrue("a@b.com".equals(body.email()));
    assertTrue(Boolean.TRUE.equals(body.emailConfirmed()));
    assertTrue("1".equals(body.phoneNumber()));
  }
}
