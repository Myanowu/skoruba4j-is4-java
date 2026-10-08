package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AccountChooserTest {

  @Test
  void approveRoundTrip() {
    MockHttpSession session = new MockHttpSession();
    String returnUrl =
        "/connect/authorize?response_type=code&client_id=x&redirect_uri=http://127.0.0.1/cb&scope=openid";
    assertFalse(AccountChooser.isApproved(session, returnUrl));
    AccountChooser.markApproved(session, returnUrl);
    assertTrue(AccountChooser.isApproved(session, returnUrl));
    AccountChooser.clearApproved(session);
    assertFalse(AccountChooser.isApproved(session, returnUrl));
  }

  @Test
  void promptNoneSkipsChooser() {
    assertTrue(AccountChooser.skipForPrompt("none"));
    assertTrue(AccountChooser.skipForPrompt("consent none"));
    assertFalse(AccountChooser.skipForPrompt("select_account"));
    assertTrue(AccountChooser.wantsSelectAccount("select_account"));
    assertTrue(AccountChooser.wantsSelectAccount("login"));
  }

  @Test
  void chooseRedirectEncodesReturnUrl() {
    String redirect =
        AccountChooser.chooseRedirect(
            "/connect/authorize?response_type=code&client_id=a&redirect_uri=http://x/cb&scope=openid");
    assertTrue(redirect.startsWith("/login/choose?ReturnUrl="));
    assertEquals("/login/choose", AccountChooser.chooseRedirect("/evil"));
  }
}
