package com.myano.skoruba4j.adminapi.web.ui;

import com.myano.skoruba4j.adminapi.security.AdminApiAccess;
import com.myano.skoruba4j.adminapi.security.UiIdentityUser;
import com.myano.skoruba4j.adminapi.web.ui.ApiDebugPresets.ApiSection;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Self-drawn Admin API console HTML (not Skoruba cshtml). */
public final class ApiUiHtml {
  private static volatile String requiredAdminRole = "MyRole";

  private ApiUiHtml() {}

  /** Called once from Spring config so HTML gates use the same role as SecurityFilterChain. */
  public static void setRequiredAdminRole(String role) {
    if (role != null && !role.isBlank()) {
      requiredAdminRole = role.trim();
    }
  }

  public record DebugEndpoint(String label, String method, String path, String body) {
    public DebugEndpoint(String label, String method, String path) {
      this(label, method, path, "");
    }
  }

  public static String page(String title, String flash, String body) {
    return page(title, flash, body, null, List.of());
  }

  public static String page(
      String title, String flash, String body, List<DebugEndpoint> debugEndpoints) {
    return page(title, flash, body, null, debugEndpoints);
  }

  public static String page(
      String title,
      String flash,
      String body,
      String sectionKey,
      List<DebugEndpoint> debugEndpoints) {
    // API nav + debug only for admin-role principals (defense in depth; SecurityFilterChain also enforces).
    boolean admin = hasAdminAccess();
    List<DebugEndpoint> endpoints =
        admin
            ? (debugEndpoints != null && !debugEndpoints.isEmpty()
                ? debugEndpoints
                : (sectionKey != null ? ApiDebugPresets.endpointsFor(sectionKey) : List.of()))
            : List.of();
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    html.append("<title>").append(esc(title)).append(" · Skoruba4j Admin API</title>");
    html.append("<style>").append(css()).append("</style></head><body");
    if (admin) {
      html.append(" class=\"app\"");
    }
    html.append(">");
    if (admin) {
      html.append("<header><div class=\"bar\">");
      html.append("<a class=\"brand\" href=\"/\">Skoruba4j <span>API</span></a>");
      html.append("<div class=\"who\">");
      html.append(esc(displayName()));
      html.append(" · <a href=\"/logout\">Sign out</a></div>");
      html.append("</div></header>");
      html.append("<div class=\"shell\">");
      html.append(apiSidebar(sectionKey, endpoints));
      html.append("<div class=\"content\">");
    }
    html.append("<main>");
    if (flash != null && !flash.isBlank()) {
      html.append("<p class=\"flash\">").append(esc(flash)).append("</p>");
    }
    if (admin) {
      html.append(body);
    } else {
      html.append(
          "<p class=\"flash\">"
              + esc(LoginFlash.adminAccessDenied())
              + "</p>");
    }
    boolean showDebug =
        admin && sectionKey != null && endpoints != null && !endpoints.isEmpty();
    if (showDebug) {
      html.append(debugPanel(title, endpoints));
    }
    html.append("</main>");
    if (admin) {
      html.append("</div></div>");
      if (showDebug) {
        html.append(debugScript());
      }
    }
    html.append("</body></html>");
    return html.toString();
  }

  public static String loginPage(String body) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    html.append("<title>Sign in · Skoruba4j Admin API</title>");
    html.append("<style>").append(css()).append("</style></head><body class=\"gate\">");
    html.append("<main>");
    html.append(body);
    html.append("</main></body></html>");
    return html.toString();
  }

  public static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  public static String jsString(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("</", "<\\/");
  }

  private static String apiSidebar(String sectionKey, List<DebugEndpoint> activeEndpoints) {
    StringBuilder html = new StringBuilder();
    html.append("<aside class=\"api-nav\" aria-label=\"API menu\">");
    html.append("<div class=\"api-nav-head\">");
    html.append("<a class=\"api-nav-title\" href=\"/ui/api\">API</a>");
    html.append("<p class=\"muted\">Select an object</p>");
    html.append("</div>");
    html.append("<ul class=\"api-objects\">");
    for (ApiSection section : ApiDebugPresets.sections()) {
      boolean active =
          sectionKey != null && sectionKey.equalsIgnoreCase(section.key());
      html.append("<li class=\"api-object").append(active ? " is-active" : "").append("\">");
      html.append("<a class=\"api-object-link\" href=\"")
          .append(esc(section.href()))
          .append("\">")
          .append(esc(section.label()))
          .append("<span class=\"count\">")
          .append(section.endpoints().size())
          .append("</span></a>");
      if (active) {
        html.append("<ul class=\"api-ops\">");
        List<DebugEndpoint> ops =
            activeEndpoints != null && !activeEndpoints.isEmpty()
                ? activeEndpoints
                : section.endpoints();
        for (int i = 0; i < ops.size(); i++) {
          DebugEndpoint ep = ops.get(i);
          html.append("<li><button type=\"button\" class=\"api-op\" data-dbg-i=\"")
              .append(i)
              .append("\" title=\"")
              .append(esc(ep.path()))
              .append("\"><span class=\"verb verb-")
              .append(esc(ep.method().toLowerCase()))
              .append("\">")
              .append(esc(ep.method()))
              .append("</span><span class=\"op-label\">")
              .append(esc(ep.label()))
              .append("</span></button></li>");
        }
        html.append("</ul>");
      }
      html.append("</li>");
    }
    html.append("</ul></aside>");
    return html.toString();
  }

  private static String debugPanel(String section, List<DebugEndpoint> endpoints) {
    StringBuilder html = new StringBuilder();
    html.append("<section class=\"debug\" id=\"apiDebug\">");
    html.append("<div class=\"debug-head\">");
    html.append("<h2>API debug · ").append(esc(section)).append("</h2>");
    html.append(
        "<p class=\"muted\">Pick an operation in the left API menu, edit path/body, then Send."
            + " Uses your browser session; machine clients still use JWT Bearer.</p>");
    html.append("</div>");
    html.append("<div class=\"debug-form\">");
    html.append("<div class=\"row2\">");
    html.append("<label>Method<select id=\"dbgMethod\">");
    for (String m : List.of("GET", "POST", "PUT", "DELETE")) {
      html.append("<option>").append(m).append("</option>");
    }
    html.append("</select></label>");
    html.append(
        "<label class=\"grow\">Path<input id=\"dbgPath\" type=\"text\" spellcheck=\"false\""
            + " placeholder=\"/api/...\"></label>");
    html.append("</div>");
    html.append(
        "<label>Body (JSON)<textarea id=\"dbgBody\" rows=\"8\" spellcheck=\"false\""
            + " placeholder=\"{}\"></textarea></label>");
    html.append("<button type=\"button\" id=\"dbgSend\" class=\"btn\">Send</button>");
    html.append("<pre id=\"dbgOut\" class=\"debug-out\">Ready.</pre>");
    html.append("</div>");
    html.append("<script type=\"application/json\" id=\"dbgPresets\">");
    html.append(presetsJson(endpoints));
    html.append("</script>");
    html.append("</section>");
    return html.toString();
  }

  private static String presetsJson(List<DebugEndpoint> endpoints) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < endpoints.size(); i++) {
      DebugEndpoint ep = endpoints.get(i);
      if (i > 0) {
        json.append(',');
      }
      json.append("{\"label\":\"")
          .append(jsString(ep.label()))
          .append("\",\"method\":\"")
          .append(jsString(ep.method()))
          .append("\",\"path\":\"")
          .append(jsString(ep.path()))
          .append("\",\"body\":\"")
          .append(jsString(ep.body() == null ? "" : ep.body()))
          .append("\"}");
    }
    json.append(']');
    return json.toString();
  }

  private static String debugScript() {
    return """
        <script>
        (function(){
          const presetsEl = document.getElementById('dbgPresets');
          if (!presetsEl) return;
          const presets = JSON.parse(presetsEl.textContent || '[]');
          const methodEl = document.getElementById('dbgMethod');
          const pathEl = document.getElementById('dbgPath');
          const bodyEl = document.getElementById('dbgBody');
          const outEl = document.getElementById('dbgOut');
          function markActive(i){
            document.querySelectorAll('.api-op, .preset').forEach(el => {
              el.classList.toggle('is-selected', Number(el.getAttribute('data-dbg-i')) === i);
            });
          }
          function load(i){
            const p = presets[i];
            if (!p) return;
            methodEl.value = p.method || 'GET';
            pathEl.value = p.path || '';
            bodyEl.value = p.body || '';
            markActive(i);
            const dbg = document.getElementById('apiDebug');
            if (dbg) dbg.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
          }
          document.querySelectorAll('[data-dbg-i]').forEach(btn => {
            btn.addEventListener('click', () => load(Number(btn.getAttribute('data-dbg-i'))));
          });
          if (presets.length) load(0);
          document.getElementById('dbgSend').addEventListener('click', async () => {
            const method = methodEl.value;
            const path = (pathEl.value || '').trim();
            outEl.textContent = 'Sending…';
            if (!path.startsWith('/api/') && path !== '/health') {
              outEl.textContent = 'Path must start with /api/ (or be /health)';
              return;
            }
            const opts = {
              method,
              credentials: 'same-origin',
              headers: { 'Accept': 'application/json' }
            };
            if (method !== 'GET' && method !== 'DELETE') {
              opts.headers['Content-Type'] = 'application/json';
              opts.body = bodyEl.value && bodyEl.value.trim() ? bodyEl.value : '{}';
            }
            try {
              const res = await fetch(path, opts);
              const text = await res.text();
              let pretty = text;
              try { pretty = JSON.stringify(JSON.parse(text), null, 2); } catch (_) {}
              outEl.textContent = res.status + ' ' + res.statusText + '\\n\\n' + pretty;
            } catch (e) {
              outEl.textContent = String(e);
            }
          });
        })();
        </script>
        """;
  }

  private static boolean hasAdminAccess() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || auth instanceof AnonymousAuthenticationToken) {
      return false;
    }
    return AdminApiAccess.hasAdminRole(auth, requiredAdminRole);
  }

  private static String displayName() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return "";
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof UiIdentityUser user) {
      return user.userName();
    }
    if (principal instanceof OidcUser oidc) {
      if (oidc.getPreferredUsername() != null && !oidc.getPreferredUsername().isBlank()) {
        return oidc.getPreferredUsername();
      }
      if (oidc.getEmail() != null && !oidc.getEmail().isBlank()) {
        return oidc.getEmail();
      }
      return oidc.getSubject() == null ? "" : oidc.getSubject();
    }
    return auth.getName() == null ? "" : auth.getName();
  }

  private static String css() {
    return """
        :root{--bg:#0f1419;--card:#1a222c;--text:#e7eef7;--muted:#9aabbd;--line:#2a3542;--accent:#3d8bfd;--err:#ff6b6b;--side:#121820}
        *{box-sizing:border-box}body{margin:0;font:15px/1.45 system-ui,Segoe UI,sans-serif;background:var(--bg);color:var(--text)}
        a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
        header{border-bottom:1px solid var(--line);background:var(--side);position:sticky;top:0;z-index:5}
        .bar{max-width:none;margin:0;padding:.75rem 1.25rem;display:flex;gap:1rem;align-items:center;justify-content:space-between}
        .brand{font-weight:700;color:var(--text)}.brand span{color:var(--muted);font-weight:500;margin-left:.25rem}
        .who{color:var(--muted);font-size:.9rem}
        body.app{min-height:100vh}
        .shell{display:flex;align-items:stretch;min-height:calc(100vh - 53px)}
        .api-nav{width:17.5rem;flex:0 0 17.5rem;background:var(--side);border-right:1px solid var(--line);padding:.9rem .7rem 1.5rem;overflow:auto}
        .api-nav-head{padding:0 .45rem .75rem;border-bottom:1px solid var(--line);margin-bottom:.55rem}
        .api-nav-title{display:block;font-weight:700;color:var(--text);font-size:1rem;letter-spacing:.02em}
        .api-nav-head .muted{margin:.2rem 0 0;font-size:.8rem}
        .api-objects,.api-ops{list-style:none;margin:0;padding:0}
        .api-object{margin:.15rem 0}
        .api-object-link{display:flex;align-items:center;justify-content:space-between;gap:.5rem;padding:.45rem .55rem;border-radius:7px;color:var(--text);font-weight:600}
        .api-object-link:hover{background:#1a2430;text-decoration:none}
        .api-object.is-active>.api-object-link{background:#1e2c3d;color:#fff;box-shadow:inset 3px 0 0 var(--accent)}
        .api-object-link .count{color:var(--muted);font-size:.75rem;font-weight:600;background:#0f1419;border:1px solid var(--line);border-radius:999px;padding:.05rem .4rem}
        .api-ops{margin:.2rem 0 .45rem .35rem;padding-left:.35rem;border-left:1px solid var(--line)}
        .api-op{display:flex;align-items:center;gap:.45rem;width:100%;margin:0;padding:.32rem .4rem;border:0;border-radius:6px;background:transparent;color:var(--text);font:inherit;font-size:.82rem;font-weight:500;text-align:left;cursor:pointer}
        .api-op:hover{background:#1a2430}
        .api-op.is-selected{background:#243041}
        .verb{flex:0 0 3.4rem;font-size:.68rem;font-weight:700;letter-spacing:.02em;color:var(--muted)}
        .verb-get{color:#6bcB77}.verb-post{color:#f0c674}.verb-put{color:#61afef}.verb-delete{color:#e06c75}
        .op-label{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
        .content{flex:1;min-width:0}
        main{max-width:1100px;margin:0 auto;padding:1.25rem}
        body.gate{min-height:100vh;display:flex;align-items:center;background:radial-gradient(900px 320px at 50% -8%,#1a2a3d 0%,var(--bg) 48%)}
        body.gate main{max-width:26rem;margin:0 auto;padding:1.5rem;width:100%}
        .card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:1.25rem 1.35rem}
        .login-card{padding:2rem 1.75rem;box-shadow:0 16px 40px rgba(0,0,0,.28);position:relative;z-index:1}
        .kicker{margin:0 0 .35rem;font-size:.75rem;letter-spacing:.12em;text-transform:uppercase;color:var(--muted);font-weight:700}
        .lead{margin:0 0 1.15rem}
        h1{font-size:1.35rem;margin:0 0 .5rem}h2{font-size:1.1rem;margin:0 0 .75rem}
        .muted{color:var(--muted)}.flash{background:#2a1a1a;border:1px solid #5a2a2a;color:#ffc9c9;padding:.65rem .8rem;border-radius:8px;margin:0 0 1rem;word-break:break-word;overflow-wrap:anywhere;max-width:100%}
        .login-card a.sign-in,.login-card button{position:relative;z-index:2}
        label{display:block;margin:.65rem 0 .25rem;color:var(--muted);font-size:.9rem}
        input[type=text],input[type=password],input[type=search],select,textarea{width:100%;padding:.55rem .65rem;border-radius:8px;border:1px solid var(--line);background:#0f1419;color:var(--text);font:inherit}
        textarea{font-family:ui-monospace,Consolas,monospace;font-size:.85rem}
        button,.btn{display:inline-block;margin-top:.9rem;padding:.55rem 1rem;border:0;border-radius:8px;background:var(--accent);color:#fff;font-weight:600;cursor:pointer;text-decoration:none}
        body.gate button{display:block;width:100%;padding:.85rem 1rem;border-radius:10px;margin-top:1.15rem}
        a.sign-in{display:block;width:100%;margin:0;padding:.85rem 1rem;border-radius:10px;border:1px solid var(--line);background:transparent;color:var(--text);font-weight:650;text-align:center;text-decoration:none;box-sizing:border-box}
        a.sign-in:hover{background:#243041;text-decoration:none}
        .login-or{display:flex;align-items:center;gap:.75rem;margin:1.1rem 0 .85rem;color:var(--muted);font-size:.78rem;font-weight:650;letter-spacing:.04em;text-transform:uppercase}
        .login-or:before,.login-or:after{content:"";flex:1;height:1px;background:var(--line)}
        .login-or span{flex:0 0 auto}
        .oidc-primary{margin:0 0 .55rem}
        table{width:100%;border-collapse:collapse;margin-top:.75rem}th,td{border-bottom:1px solid var(--line);padding:.5rem .4rem;text-align:left}
        th{color:var(--muted);font-weight:600;font-size:.85rem}code{font-size:.9em}
        .grid{display:grid;gap:.75rem;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));margin-top:1rem}
        .tile{display:block;background:var(--card);border:1px solid var(--line);border-radius:10px;padding:1rem;color:var(--text)}.tile:hover{border-color:var(--accent);text-decoration:none}
        .tile strong{display:block;margin-bottom:.25rem}
        .debug{margin-top:2rem;padding:1.1rem 1.2rem;border:1px solid var(--line);border-radius:10px;background:#121820}
        .debug-head h2{margin:0 0 .35rem}
        .debug-form .row2{display:flex;gap:.75rem;align-items:end}
        .debug-form .row2 label{flex:0 0 8rem}.debug-form .row2 label.grow{flex:1}
        .debug-out{margin-top:1rem;padding:.75rem;background:#0a0e13;border:1px solid var(--line);border-radius:8px;max-height:28rem;overflow:auto;white-space:pre-wrap;word-break:break-word;font:12px/1.4 ui-monospace,Consolas,monospace;color:#c6d6e8}
        @media (max-width:860px){
          .shell{flex-direction:column}
          .api-nav{width:100%;flex:none;border-right:0;border-bottom:1px solid var(--line);max-height:42vh}
        }
        """;
  }
}
