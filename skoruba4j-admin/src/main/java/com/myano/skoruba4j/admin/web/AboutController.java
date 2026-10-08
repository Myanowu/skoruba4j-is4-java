package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AboutController {

  @GetMapping(value = {"/about", "/admin/about"}, produces = "text/html;charset=UTF-8")
  @ResponseBody
  public String about(HttpServletRequest request) {
    return AdminHtml.page(
        Messages.t("about.title"),
        AdminRequests.flash(request),
        AdminRequests.csrfName(request),
        AdminRequests.csrfToken(request),
        AdminHtml.about());
  }
}
