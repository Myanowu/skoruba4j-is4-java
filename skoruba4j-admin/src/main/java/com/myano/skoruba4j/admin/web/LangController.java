package com.myano.skoruba4j.admin.web;

import com.myano.skoruba4j.i18n.LangSwitcher;
import com.myano.skoruba4j.i18n.UiLocale;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/** Sets {@link UiLocale#COOKIE} and redirects back. */
@Controller
public class LangController {

  @GetMapping("/lang")
  public RedirectView change(
      @RequestParam("code") String code,
      @RequestParam(value = "return", required = false) String returnPath,
      HttpServletResponse response) {
    UiLocale locale = UiLocale.parse(code).orElse(UiLocale.EN);
    Cookie cookie = new Cookie(UiLocale.COOKIE, locale.code());
    cookie.setPath("/");
    cookie.setMaxAge(365 * 24 * 60 * 60);
    cookie.setHttpOnly(false);
    response.addCookie(cookie);
    RedirectView view = new RedirectView(LangSwitcher.safeReturn(returnPath));
    view.setContextRelative(true);
    return view;
  }
}
