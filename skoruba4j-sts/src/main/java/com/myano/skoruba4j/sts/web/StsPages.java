package com.myano.skoruba4j.sts.web;

/** Shared chrome for STS HTML (self-drawn; not Skoruba cshtml). */
final class StsPages {
  private StsPages() {}

  static String document(String title, String bodyClass, String inner) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    html.append("<title>").append(esc(title)).append("</title>");
    html.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"");
    html.append("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 512 512'%3E");
    html.append("%3Crect width='512' height='512' rx='118' fill='%236B1C24'/%3E");
    html.append("%3Cpath fill='%23F0E4C8' d='M256 72L420 154v112c0 123-164 184-164 184S92 389 92 266V154z'/%3E");
    html.append("%3Ccircle cx='256' cy='230' r='52' fill='%236B1C24'/%3E");
    html.append("%3Cpath fill='%236B1C24' d='M233 256h46l13 102H220z'/%3E");
    html.append("%3C/svg%3E\">");
    html.append("<style>").append(css()).append("</style></head>");
    html.append("<body class=\"").append(esc(bodyClass)).append("\">");
    html.append(inner);
    html.append("<dialog id=\"jsonDlg\" class=\"dlg\" onclick=\"if(event.target===this)this.close()\">");
    html.append("<div class=\"dlg-head\"><strong id=\"jsonDlgTitle\"></strong>");
    html.append("<button type=\"button\" class=\"dlg-x\" onclick=\"document.getElementById('jsonDlg').close()\">Close</button></div>");
    html.append("<p id=\"jsonDlgLead\" class=\"dlg-lead\" hidden></p>");
    html.append("<div id=\"jsonDlgBody\" class=\"dlg-body\"></div></dialog>");
    html.append("<script>");
    html.append("function h(s){return String(s==null?'':s).replace(/[&<>\"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c];});}");
    html.append("function toggleSecret(id,btn){var i=document.getElementById(id);if(!i)return;");
    html.append("i.type=i.type==='password'?'text':'password';");
    html.append("if(btn)btn.textContent=i.type==='password'?'Show':'Hide';}");
    html.append("function showDlg(title,lead){var d=document.getElementById('jsonDlg');var t=document.getElementById('jsonDlgTitle');");
    html.append("var l=document.getElementById('jsonDlgLead');if(t)t.textContent=title||'';");
    html.append("if(l){if(lead){l.hidden=false;l.textContent=lead;}else{l.hidden=true;l.textContent='';}}");
    html.append("if(d&&d.showModal)d.showModal();}");
    html.append("function openJson(url,title){var b=document.getElementById('jsonDlgBody');if(!b)return;");
    html.append("showDlg(title||url,'');b.textContent='Loading…';");
    html.append("fetch(url,{credentials:'same-origin'}).then(function(r){return r.text();}).then(function(x){");
    html.append("var pretty=x||'(empty)';try{pretty=JSON.stringify(JSON.parse(x),null,2);}catch(e){}");
    html.append("b.innerHTML='<pre>'+h(pretty)+'</pre>';");
    html.append("}).catch(function(){b.textContent='Could not load '+url;});}");
    html.append("function openGrants(){var b=document.getElementById('jsonDlgBody');if(!b)return;");
    html.append("var help=document.getElementById('grantsHelp');");
    html.append("showDlg('Grants',help?help.textContent:'');b.textContent='Loading…';");
    html.append("fetch('/grants.json',{credentials:'same-origin'}).then(function(r){if(!r.ok)throw new Error();return r.json();}).then(function(rows){");
    html.append("if(!rows||!rows.length){b.innerHTML='<p>No persisted grants for this account.</p>';return;}");
    html.append("var html='<table><thead><tr><th>Client</th><th>Type</th><th>Created (UTC)</th><th>Expires (UTC)</th><th></th></tr></thead><tbody>';");
    html.append("rows.forEach(function(g){html+='<tr><td>'+h(g.clientId)+'</td><td>'+h(g.type)+'</td><td>'+h(g.created)+'</td><td>'+h(g.expires)+'</td>';");
    html.append("html+='<td><button type=\"button\" data-key=\"'+encodeURIComponent(g.key||'')+'\" onclick=\"revokeGrant(decodeURIComponent(this.getAttribute(\\'data-key\\')))\">Revoke</button></td></tr>';});");
    html.append("html+='</tbody></table><p class=\"actions\"><button type=\"button\" onclick=\"revokeAllGrants()\">Revoke all</button></p>';");
    html.append("b.innerHTML=html;}).catch(function(){b.textContent='Could not load grants.';});}");
    html.append("function postGrant(url,body){return fetch(url,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body||'dialog=1'});}");
    html.append("function revokeGrant(key){postGrant('/grants/revoke','dialog=1&key='+encodeURIComponent(key)).then(openGrants);}");
    html.append("function revokeAllGrants(){postGrant('/grants/revoke-all','dialog=1').then(openGrants);}");
    html.append("</script>");
    html.append("</body></html>");
    return html.toString();
  }

  static String card(String kicker, String title, String subtitle, String inner) {
    StringBuilder html = new StringBuilder();
    html.append("<main class=\"card\">");
    html.append("<p class=\"kicker\">").append(esc(kicker)).append("</p>");
    html.append("<h1>").append(esc(title)).append("</h1>");
    if (subtitle != null && !subtitle.isBlank()) {
      html.append("<p class=\"lead\">").append(esc(subtitle)).append("</p>");
    }
    html.append(inner);
    html.append("</main>");
    return html.toString();
  }

  static String app(String title, String inner) {
    StringBuilder html = new StringBuilder();
    html.append("<div class=\"app\">");
    html.append("<header class=\"top\">");
    html.append("<div><p class=\"kicker\">Skoruba4j STS</p><h1>").append(esc(title)).append("</h1></div>");
    html.append("<a class=\"ghost\" href=\"/logout\">Sign out</a>");
    html.append("</header>");
    html.append(inner);
    html.append("</div>");
    return html.toString();
  }

  static String notice(String kind, String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return "<p class=\"note " + esc(kind) + "\">" + esc(text) + "</p>";
  }

  static String field(String id, String name, String type, String autocomplete, boolean autofocus) {
    StringBuilder html = new StringBuilder();
    html.append("<input id=\"")
        .append(esc(id))
        .append("\" name=\"")
        .append(esc(name))
        .append("\" type=\"")
        .append(esc(type))
        .append("\" autocomplete=\"")
        .append(esc(autocomplete))
        .append("\" required");
    if (autofocus) {
      html.append(" autofocus");
    }
    html.append(">");
    return html.toString();
  }

  static String passwordField(String id, String name, String autocomplete, boolean autofocus) {
    return "<div class=\"secret\">"
        + field(id, name, "password", autocomplete, autofocus)
        + "<button type=\"button\" class=\"eye\" onclick=\"toggleSecret('"
        + esc(id)
        + "',this)\">Show</button></div>";
  }

  static String hidden(String name, String value) {
    return "<input type=\"hidden\" name=\"" + esc(name) + "\" value=\"" + esc(value) + "\">";
  }

  static String esc(String value) {
    return LoginPage.esc(value);
  }

  private static String css() {
    return """
        :root{--ink:#111827;--muted:#6b7280;--line:#e5e7eb;--fill:#f3f4f6;--card:#fff;--accent:#111827;--link:#2563eb;--ok:#047857;--err:#b91c1c}
        *{box-sizing:border-box}
        body{margin:0;font-family:Segoe UI,system-ui,sans-serif;color:var(--ink);background:#eef2f7;line-height:1.45}
        body.auth{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:1.5rem;
          background:radial-gradient(900px 320px at 50% -8%,#dbeafe 0%,#eef2f7 42%)}
        body.app-body{min-height:100vh;padding:1.5rem}
        .card,.app{width:min(28rem,100%);background:var(--card);border-radius:16px;box-shadow:0 16px 40px rgba(15,23,42,.08);padding:2rem}
        .app{width:min(58rem,100%);margin:0 auto}
        .kicker{margin:0 0 .35rem;font-size:.75rem;letter-spacing:.12em;text-transform:uppercase;color:var(--muted);font-weight:700}
        h1{margin:0 0 .35rem;font-size:1.65rem}
        .lead{margin:0 0 1.25rem;color:var(--muted)}
        label{display:block;margin:.9rem 0 .35rem;font-weight:600;font-size:.92rem}
        .row{display:flex;justify-content:space-between;align-items:baseline;gap:1rem}
        input,button,a.btn{font:inherit}
        input[type=text],input[type=email],input[type=password]{width:100%;border:1px solid transparent;background:var(--fill);border-radius:10px;padding:.8rem .95rem}
        input:focus{outline:2px solid var(--accent);background:#fff}
        .secret{position:relative}
        .secret input{padding-right:4.2rem}
        .eye{position:absolute;right:.35rem;top:50%;transform:translateY(-50%);border:0;background:transparent;color:var(--muted);cursor:pointer;padding:.35rem .55rem}
        button.primary,a.btn,a.sign-in{display:block;width:100%;margin-top:1.15rem;background:var(--accent);color:#fff;border:0;border-radius:10px;padding:.85rem 1rem;font-weight:650;text-align:center;text-decoration:none;cursor:pointer}
        a.sign-in{background:#fff;color:var(--ink);border:1px solid var(--line);margin-top:0}
        a.sign-in:hover{background:var(--fill);text-decoration:none}
        a.account{display:flex;align-items:center;gap:.9rem;width:100%;margin:0 0 .55rem;padding:.85rem .95rem;border:1px solid var(--line);border-radius:12px;background:#fff;color:var(--ink);text-decoration:none}
        a.account:hover{background:var(--fill);text-decoration:none;border-color:#cbd5e1}
        a.account-other{margin-top:.85rem}
        .avatar{flex:0 0 2.5rem;width:2.5rem;height:2.5rem;border-radius:999px;background:#1d4ed8;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700}
        .avatar-muted{background:var(--fill);color:var(--muted);border:1px solid var(--line)}
        .account-text{display:flex;flex-direction:column;gap:.1rem;min-width:0}
        .account-text strong{font-weight:650}
        .login-or{display:flex;align-items:center;gap:.75rem;margin:1.1rem 0 .85rem;color:var(--muted);font-size:.78rem;font-weight:650;letter-spacing:.04em;text-transform:uppercase}
        .login-or:before,.login-or:after{content:"";flex:1;height:1px;background:var(--line)}
        .login-or span{flex:0 0 auto}
        .oidc-primary{margin:0}
        .wa-qr{margin:1rem 0;text-align:center}
        .wa-qr img{display:inline-block;border-radius:12px;border:1px solid var(--line);background:#fff;padding:.5rem}
        .wa-inline{margin:0 0 1rem;padding:1rem;border:1px solid var(--line);border-radius:12px;background:#fafafa;text-align:center}
        a{color:var(--link);text-decoration:none}
        a:hover{text-decoration:underline}
        .note{border-radius:10px;padding:.7rem .85rem;margin:0 0 1rem}
        .note.err{background:#fef2f2;color:var(--err)}
        .note.ok{background:#ecfdf5;color:var(--ok)}
        .muted{color:var(--muted);font-size:.92rem}
        .top{display:flex;justify-content:space-between;align-items:flex-start;gap:1rem;margin-bottom:1.25rem}
        .ghost{border:1px solid var(--line);border-radius:999px;padding:.4rem .9rem;color:var(--ink)}
        .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(15rem,1fr));gap:1rem}
        .tile{border:1px solid var(--line);border-radius:14px;padding:1.1rem 1.15rem;background:#fff;display:flex;flex-direction:column;gap:.35rem}
        .tile h2{margin:0;font-size:1.05rem}
        .tile p{margin:0;color:var(--muted);font-size:.92rem;flex:1}
        .tile a.btn,.tile button.btn{width:auto;margin-top:.85rem;padding:.55rem .9rem}
        .tile a.btn.outline,.tile button.btn.outline{background:#fff;color:var(--ink);border:1px solid var(--line)}
        button.btn{border:0}
        dialog.dlg{border:0;border-radius:16px;padding:0;width:min(44rem,calc(100vw - 2rem));max-height:min(80vh,40rem);box-shadow:0 24px 64px rgba(15,23,42,.2)}
        dialog.dlg::backdrop{background:rgba(15,23,42,.45)}
        .dlg-head{display:flex;justify-content:space-between;align-items:center;gap:1rem;padding:1rem 1.15rem;border-bottom:1px solid var(--line)}
        .dlg-x{border:1px solid var(--line);background:#fff;border-radius:8px;padding:.35rem .7rem;cursor:pointer}
        .dlg-lead{margin:0;padding:.85rem 1.15rem .25rem;color:var(--muted);font-size:.92rem}
        .dlg-body{padding:1rem 1.15rem;overflow:auto;max-height:min(64vh,32rem)}
        .dlg-body pre{margin:0;font:12px/1.45 ui-monospace,Consolas,monospace;white-space:pre-wrap;word-break:break-word}
        table{width:100%;border-collapse:collapse;font-size:.92rem}
        th,td{text-align:left;padding:.55rem .4rem;border-bottom:1px solid var(--line);vertical-align:top}
        th{color:var(--muted);font-weight:600}
        code{font-size:.85em;background:var(--fill);padding:.1rem .35rem;border-radius:6px}
        code.setting{display:block;margin:0 0 1.15rem;padding:.7rem .85rem;font-size:.82rem;line-height:1.4;word-break:break-all}
        .dl{display:grid;grid-template-columns:7rem 1fr;gap:.35rem .75rem;margin:0}
        .dl dt{color:var(--muted)}
        .actions{display:flex;gap:.5rem;flex-wrap:wrap;margin-top:1rem}
        .actions form{margin:0}
        .actions button{background:transparent;border:1px solid var(--line);border-radius:8px;padding:.35rem .7rem;cursor:pointer}
        """;
  }
}
