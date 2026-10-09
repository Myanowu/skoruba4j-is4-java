package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.configstore.ClientConfiguration.ClientClaim;
import java.util.List;
import org.junit.jupiter.api.Test;

class Is4ClientClaimsTest {

  @Test
  void clientCredentialsAlwaysIncludesPrefixedClaims() {
    assertTrue(Is4ClientClaims.includeOnAccessToken(true, false));
    assertFalse(Is4ClientClaims.includeOnAccessToken(false, false));
    assertTrue(Is4ClientClaims.includeOnAccessToken(false, true));
    var claims =
        Is4ClientClaims.accessTokenClaims(
            "client_",
            List.of(new ClientClaim(1, "role", "Admin"), new ClientClaim(2, "role", "MyRole")));
    assertEquals(List.of("Admin", "MyRole"), claims.get("client_role"));
  }
}
