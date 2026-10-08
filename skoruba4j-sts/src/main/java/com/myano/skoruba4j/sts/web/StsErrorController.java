package com.myano.skoruba4j.sts.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class StsErrorController implements ErrorController {
  private static final Logger log = LoggerFactory.getLogger(StsErrorController.class);

  @RequestMapping("/error")
  @ResponseBody
  public String error(HttpServletRequest request) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
    Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    String code = status == null ? "" : status.toString();
    String detail = message == null ? "" : message.toString();
    if (exception instanceof Throwable thrown) {
      log.error("STS error {} {}", code, detail, thrown);
      if (detail.isBlank() && thrown.getMessage() != null) {
        detail = thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
      }
    } else if (exception != null) {
      log.error("STS error {} {} {}", code, detail, exception);
    } else {
      log.error("STS error {} {}", code, detail);
    }
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Error</title>");
    html.append("<style>body{font-family:Segoe UI,sans-serif;margin:2rem auto;max-width:36rem}</style></head><body>");
    html.append("<h1>Sign-in could not finish</h1>");
    html.append("<p>Status: <code>").append(LoginPage.esc(code)).append("</code></p>");
    if (!detail.isBlank()) {
      html.append("<p>").append(LoginPage.esc(detail)).append("</p>");
    }
    html.append("<p><a href=\"/login\">Back to sign in</a></p>");
    html.append("</body></html>");
    return html.toString();
  }
}
