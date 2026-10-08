package com.myano.skoruba4j.admin.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientCreateTemplateTest {

  @Test
  void fromQueryDefaultsToEmpty() {
    assertEquals(ClientCreateTemplate.EMPTY, ClientCreateTemplate.fromQuery(null));
    assertEquals(ClientCreateTemplate.EMPTY, ClientCreateTemplate.fromQuery("nope"));
  }

  @Test
  void spaIsPublicPkceClient() {
    ClientCreateTemplate spa = ClientCreateTemplate.fromQuery("spa");
    assertEquals(ClientCreateTemplate.SPA, spa);
    assertFalse(spa.requireClientSecret());
    assertTrue(spa.requirePkce());
    assertTrue(spa.grantTypes().contains("authorization_code"));
    assertTrue(spa.allowOfflineAccess());
  }

  @Test
  void machineIsClientCredentialsWithSecret() {
    ClientCreateTemplate machine = ClientCreateTemplate.fromQuery("MACHINE");
    assertEquals(ClientCreateTemplate.MACHINE, machine);
    assertTrue(machine.requireClientSecret());
    assertFalse(machine.requirePkce());
    assertEquals("client_credentials", machine.grantTypes());
  }

  @Test
  void pickerMarksActiveTemplate() {
    String html = AdminHtml.clientTemplatePicker(ClientCreateTemplate.WEB);
    assertTrue(html.contains("data-template=\"web\""));
    assertTrue(html.contains("is-active"));
    assertTrue(html.contains("template-bar"));
    assertTrue(html.contains(">Web</button>"));
    assertTrue(html.contains("data-template-summary"));
    assertTrue(html.contains("Web app — server-side"));
  }

  @Test
  void editorScriptFindsFormOutsideTemplateBar() {
    String script = AdminHtml.clientEditorScript();
    assertTrue(script.contains("findClientForm"));
    assertTrue(script.contains("activateClientTab"));
    assertFalse(script.contains("form.editor"));
  }
}
