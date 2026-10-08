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
            List.of(new AdminDtos.UserDto("id-1", "alice", "a@example.com", true, false, false)));
    String userJson = mapper.writeValueAsString(users);
    assertTrue(userJson.contains("\"users\""));
    assertTrue(userJson.contains("\"userName\":\"alice\""));
    assertFalse(userJson.contains("passwordHash"));
  }
}
