package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class StsSessionUserSerializationTest {

  @Test
  void roundTripsForHttpSession() throws Exception {
    StsSessionUser original =
        new StsSessionUser(
            "user-1",
            Map.of("sub", "user-1", "preferred_username", "demo"),
            Map.of("sub", "user-1", "role", List.of("MyRole")),
            Map.of("email", "demo@localhost"),
            List.of(new SimpleGrantedAuthority("MyRole")));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(original);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      StsSessionUser restored = (StsSessionUser) in.readObject();
      assertEquals("demo", restored.getUsername());
      assertEquals("user-1", restored.subject());
      assertTrue(
          restored.getAuthorities().stream().anyMatch(a -> "MyRole".equals(a.getAuthority())));
    }
  }
}
