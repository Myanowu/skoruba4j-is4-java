package com.myano.orgplatform.web;

import com.myano.orgplatform.config.OrgPlatformProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginDeniedController {

  private final OrgPlatformProperties properties;

  public LoginDeniedController(OrgPlatformProperties properties) {
    this.properties = properties;
  }

  /** Shown when STS login fails or the account has no admin role. */
  @GetMapping("/login/denied")
  public void denied(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String role = escape(properties.getSecurity().getAdminRole());
    boolean error = "1".equals(request.getParameter("error"));
    String detail =
        error
            ? "無法完成 STS 登入。請確認 client <code>org-platform</code> 已在 Skoruba Admin 註冊，且 redirect URI 是這個位址的 <code>/login/oauth2/code/sts</code>。"
            : "此帳號已登入 STS，但沒有管理角色 <code>"
                + role
                + "</code>，不能使用組織、崗位和人員管理。";
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("text/html;charset=UTF-8");
    response
        .getWriter()
        .write(
            """
            <!DOCTYPE html>
            <html lang="zh-Hant">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>無法進入組織管理</title>
              <style>
                body { margin: 0; font-family: "Segoe UI", "Microsoft JhengHei", sans-serif; background: #f3f5f8; color: #1c2430; }
                main { max-width: 520px; margin: 12vh auto; background: #fff; border: 1px solid #e6e8ee; border-radius: 12px; padding: 28px 28px 24px; }
                h1 { font-size: 20px; margin: 0 0 12px; }
                p { line-height: 1.55; margin: 0 0 16px; }
                a { color: #1d4ed8; }
                code { font-family: ui-monospace, Consolas, monospace; font-size: 0.95em; }
              </style>
            </head>
            <body>
              <main>
                <h1>無法進入組織管理</h1>
                <p>%s</p>
                <p><a href="/oauth2/authorization/sts">用其他帳號登入</a></p>
              </main>
            </body>
            </html>
            """
                .formatted(detail));
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
