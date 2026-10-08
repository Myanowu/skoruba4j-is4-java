package com.myano.skoruba4j.admin.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.myano.skoruba4j.admin.security.StsSessionUser;
import com.myano.skoruba4j.i18n.UiLocale;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class AdminHtmlTest {

  @AfterEach
  void clearUser() {
    SecurityContextHolder.clearContext();
    UiLocale.clearCurrent();
  }

  @Test
  void splitDurationBreaksSecondsIntoParts() {
    assertArrayEquals(new int[] {30, 0, 0, 0}, AdminHtml.splitDuration(2_592_000));
    assertArrayEquals(new int[] {0, 1, 0, 0}, AdminHtml.splitDuration(3600));
    assertArrayEquals(new int[] {0, 0, 5, 0}, AdminHtml.splitDuration(300));
  }

  @Test
  void clientEditorControlsUseFriendlyInputs() {
    String duration = AdminHtml.durationField("accessTokenLifetime", "Access token", 3600);
    assertTrue(duration.contains("name=\"accessTokenLifetime\""));
    assertTrue(duration.contains("data-unit=\"h\""));
    assertTrue(duration.contains("3600 seconds"));
    String tokenType = AdminHtml.accessTokenTypeSelect(0);
    assertTrue(tokenType.contains("<select name=\"accessTokenType\">"));
    assertTrue(tokenType.contains("JWT (self-contained)"));
    String grants =
        AdminHtml.listPicker(
            "grantTypes", "Grants", "hint", "authorization_code", new String[] {"authorization_code"});
    assertTrue(grants.contains("data-list-picker"));
    assertTrue(grants.contains("checked"));
  }

  @Test
  void anonymousPageHasNoAdminNav() {
    String html = AdminHtml.page("Home", "saved", "_csrf", "t", "<h1>Admin</h1>");
    assertFalse(html.contains("<nav>"));
    assertFalse(html.contains("/admin/clients"));
    assertFalse(html.contains("/admin/users"));
    assertTrue(html.contains("saved"));
    assertFalse(html.contains("<details class=\"user\">"));
    assertFalse(html.contains(".cshtml"));
    assertTrue(html.contains("id=\"msg-box\""));
    assertTrue(html.contains("SkorubaMsg"));
  }

  @Test
  void dataConfirmUsesAttributesNotNativeConfirm() {
    String attrs = AdminHtml.dataConfirm("Delete this user?", "Delete user", "Delete user", true);
    assertTrue(attrs.contains("data-confirm=\"Delete this user?\""));
    assertTrue(attrs.contains("data-confirm-title=\"Delete user\""));
    assertFalse(attrs.contains("confirm("));
  }

  @Test
  void loginPageHasNoAdminNav() {
    String html =
        AdminHtml.loginPage(
            "Sign in",
            null,
            AdminHtml.loginForm("_csrf", "t", null, null, true, "https://localhost:5051/forgot-password"));
    assertTrue(html.contains("class=\"gate\""));
    assertTrue(html.contains("class=\"langs\""));
    assertFalse(html.contains("/admin/clients"));
    assertFalse(html.contains("href=\"/about\""));
    assertTrue(html.contains("Sign in with STS"));
    assertFalse(html.contains("share this login (SSO)"));
    assertFalse(html.contains("name=\"username\""));
    assertFalse(html.contains("or local password"));
    assertTrue(html.contains("<h1>Sign in</h1>"));
    assertTrue(html.contains("Skoruba4j Admin"));
    assertFalse(html.contains(".cshtml"));
  }

  @Test
  void loginPageLocalModeHasPasswordAndSts() {
    String html =
        AdminHtml.loginPage(
            "Sign in",
            null,
            AdminHtml.loginForm("_csrf", "t", null, null, false, "https://localhost:5051/forgot-password"));
    assertTrue(html.contains("class=\"gate\""));
    assertTrue(html.contains("name=\"username\""));
    assertTrue(html.contains("name=\"password\""));
    assertTrue(html.contains("name=\"_csrf\""));
    assertTrue(html.contains("Forgot your password?"));
    assertTrue(html.contains(">Sign in</button>"));
    assertTrue(html.contains("Sign in with STS"));
    assertTrue(html.contains("/oauth2/authorization/sts"));
  }

  @Test
  void loginPageShowsRoleDialogWithoutOpeningAdmin() {
    String html =
        AdminHtml.loginPage("Sign in", null, "<form></form>", true, "MyRole", "User");
    assertTrue(html.contains("<dialog class=\"notice\" open>"));
    assertTrue(html.contains("Password was accepted by STS"));
    assertTrue(html.contains("<code>MyRole</code>"));
    assertTrue(html.contains("Roles on token:"));
    assertTrue(html.contains("<code>User</code>"));
    assertFalse(html.contains("href=\"/admin/clients\""));
    assertFalse(html.contains("href=\"/logout\""));
  }

  @Test
  void loginFormLocalErrorDoesNotMentionOidc() {
    String html = AdminHtml.loginForm("_csrf", "t", "", null, false, "");
    assertTrue(html.contains("Invalid username or password"));
    assertFalse(html.contains("OIDC"));
    assertTrue(html.contains("Sign in with STS"));
  }

  @Test
  void navIsSelfDrawnAndListsMainSections() {
    signInAda();
    String html = AdminHtml.page("Home", "saved", "_csrf", "t", "<h1>Admin</h1>");
    assertTrue(html.contains("class=\"brand\" href=\"/admin\""));
    assertFalse(html.contains("<a href=\"/admin\">Home</a>"));
    assertTrue(html.contains("/admin/clients"));
    assertTrue(html.contains("/admin/users"));
    assertTrue(html.contains("href=\"/about\">About</a>"));
    assertFalse(html.contains("href=\"/health\">health</a>"));
    assertTrue(html.contains("Skoruba4j"));
    assertTrue(html.contains("rel=\"icon\""));
    assertTrue(html.contains("saved"));
    assertFalse(html.contains(".cshtml"));
    assertFalse(html.contains("ForceAdministrationRole"));
  }

  @Test
  void guideExplainsSectionsLoginAndDatabase() {
    UiLocale.setCurrent(UiLocale.EN);
    String html = AdminHtml.guide();
    assertTrue(html.contains("What this UI does"));
    assertTrue(html.contains("/admin/clients"));
    assertTrue(html.contains("/admin/users"));
    assertTrue(html.contains("/admin/grants"));
    assertTrue(html.contains("/admin/audit-logs"));
    assertTrue(html.contains("/about"));
    assertTrue(html.contains("idserver.admin.role"));
    assertTrue(html.contains("ApiResources.Name"));
    assertTrue(html.contains("idserver.admin.login-mode"));
    assertTrue(html.contains("User info"));
    assertTrue(html.contains("Sign out"));
    assertFalse(html.contains("这个界面做什么"));
    assertFalse(html.contains(".cshtml"));
    assertFalse(html.contains("ForceAdministrationRole"));
  }

  @Test
  void guideInSimplifiedChineseIsNotMixedWithEnglishChrome() {
    UiLocale.setCurrent(UiLocale.ZH_HANS);
    String html = AdminHtml.guide();
    assertTrue(html.contains("这个界面做什么"));
    assertTrue(html.contains(">客户端</a>"));
    assertTrue(html.contains(">持久授权</a>"));
    assertFalse(html.contains(">Clients</a>"));
    assertFalse(html.contains(">Persisted Grants</a>"));
  }

  @Test
  void signedInUserMenuOpensInfoDialogAndHoldsSignOut() {
    signInAda();
    String html = AdminHtml.page("Home", null, "_csrf", "t", "<h1>Admin</h1>");
    assertTrue(html.contains("<summary>ada</summary>"));
    assertFalse(html.contains("<summary>Ada Lovelace</summary>"));
    assertFalse(html.contains("<summary>ada@example.com</summary>"));
    assertTrue(html.contains("data-open-profile>User info</button>"));
    assertTrue(html.contains("<a class=\"signout\" href=\"/logout\">Sign out</a>"));
    assertTrue(html.contains("<dialog class=\"profile\" id=\"user-profile\""));
    assertTrue(html.contains("<dt>Username</dt><dd>ada</dd>"));
    assertTrue(html.contains("<dt>Name</dt><dd>Ada Lovelace</dd>"));
    assertTrue(html.contains("<dt>Email</dt><dd>ada@example.com</dd>"));
    assertTrue(html.contains("<dt>Subject</dt><dd>user-1</dd>"));
    assertTrue(html.contains("<dt>Roles</dt><dd>MyRole</dd>"));
    assertFalse(html.contains("SCOPE_openid"));
    assertFalse(html.contains(">OIDC_USER<"));
    assertFalse(html.contains("</details><a class=\"signout\""));
    assertTrue(html.contains("getElementById('user-profile')"));
    assertTrue(html.contains("showModal"));
    assertTrue(html.indexOf("<summary>ada</summary>") < html.indexOf("data-open-profile"));
    assertTrue(html.indexOf("data-open-profile") < html.indexOf("href=\"/logout\""));
    assertTrue(html.indexOf("href=\"/logout\"") < html.indexOf("</details>"));
    assertTrue(html.indexOf("</details>") < html.indexOf("<dialog class=\"profile\""));
    assertTrue(html.indexOf("<dialog class=\"profile\"") < html.indexOf("<dt>Name</dt>"));
  }

  @Test
  void aboutExplainsDropInAndIsNotKeycloak() {
    String html = AdminHtml.about();
    assertTrue(html.contains("Skoruba4j"));
    assertTrue(html.contains("OpenID Connect"));
    assertTrue(html.contains("OAuth 2.0"));
    assertTrue(html.contains("authorization_code"));
    assertTrue(html.contains("PKCE"));
    assertTrue(html.contains("IdentityServer4"));
    assertTrue(html.contains("/connect"));
    assertTrue(html.contains("ApiResources.Name"));
    assertTrue(html.contains("Apache-2.0"));
    assertTrue(html.contains("docs/ABOUT.md"));
    assertTrue(html.contains("not Keycloak"));
    assertFalse(html.contains("<h2>中文</h2>"));
    assertFalse(html.contains(".cshtml"));
    assertFalse(html.contains("ForceAdministrationRole"));
  }

  @Test
  void stsPasswordSessionShowsUserinfoAndParsedTokens() {
    StsSessionUser user =
        new StsSessionUser(
            "user-1",
            Map.of(
                "sub", "user-1",
                "preferred_username", "demo",
                "email", "demo@example.com",
                "email_verified", true),
            Map.of("sub", "user-1", "role", List.of("MyRole"), "iss", "https://sts"),
            Map.of("sub", "user-1", "name", "Demo User"),
            List.of(new SimpleGrantedAuthority("MyRole")));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    String html = AdminHtml.page("Home", null, "_csrf", "t", "<h1>Admin</h1>");
    assertTrue(html.contains("<summary>demo</summary>"));
    assertTrue(html.contains("<dt>Username</dt><dd>demo</dd>"));
    assertTrue(html.contains("<dt>Name</dt><dd>Demo User</dd>"));
    assertTrue(html.contains("<dt>Email</dt><dd>demo@example.com</dd>"));
    assertTrue(html.contains("<dt>Subject</dt><dd>user-1</dd>"));
    assertTrue(html.contains("data-token-tabs"));
    assertTrue(html.contains("data-tab=\"id-token\""));
    assertTrue(html.contains("data-tab=\"access-token\""));
    assertFalse(html.contains("UserInfo"));
    assertTrue(html.contains("&quot;preferred_username&quot;"));
  }

  private static void signInAda() {
    Instant now = Instant.now();
    OidcIdToken token =
        new OidcIdToken(
            "token",
            now,
            now.plusSeconds(300),
            Map.of(
                "sub", "user-1",
                "preferred_username", "ada",
                "name", "Ada Lovelace",
                "email", "ada@example.com"));
    OidcUser user =
        new DefaultOidcUser(
            List.of(
                new SimpleGrantedAuthority("MyRole"),
                new SimpleGrantedAuthority("SCOPE_openid"),
                new SimpleGrantedAuthority("OIDC_USER")),
            token);
    SecurityContextHolder.getContext()
        .setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "sts"));
  }
}
