const $ = (id) => document.getElementById(id);

const UNIT_TYPES = [
  ["COMPANY", "公司"],
  ["UNIT", "單位"],
  ["DEPT", "部門"],
  ["TEAM", "小組"],
];

const state = {
  tree: [],
  people: [],
  selectedId: null,
  highlightKey: null,
  ws: null,
  q: "",
  expanded: new Set(),
  loadToken: 0,
};

function esc(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function unitTypeLabel(type) {
  return UNIT_TYPES.find((row) => row[0] === type)?.[1] || type || "部門";
}

function avatarColor(name) {
  const palette = ["#1d4ed8", "#0f766e", "#b45309", "#7c3aed", "#be123c", "#0369a1"];
  let hash = 0;
  for (const ch of name || "") hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return palette[hash % palette.length];
}

function initial(name) {
  const text = (name || "?").trim();
  return text.slice(0, 1);
}

async function api(path, opts = {}) {
  const res = await fetch(path, {
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...(opts.headers || {}) },
    ...opts,
  });
  if (res.status === 401) {
    window.location.assign("/oauth2/authorization/sts");
    throw new Error("請先登入");
  }
  const text = await res.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  if (!res.ok) {
    throw new Error(typeof body === "object" && body?.error ? body.error : text || String(res.status));
  }
  return body;
}

let toastTimer;
function toast(message, kind) {
  const el = $("toast");
  el.textContent = message;
  el.className = "toast show" + (kind === "err" ? " err" : "");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 2800);
}

function flatten(nodes, acc = []) {
  for (const node of nodes || []) {
    acc.push(node);
    flatten(node.children, acc);
  }
  return acc;
}

function byIdMap() {
  const map = new Map();
  for (const node of flatten(state.tree)) map.set(node.id, node);
  return map;
}

function breadcrumb(id) {
  const map = byIdMap();
  const parts = [];
  let cur = map.get(id);
  while (cur) {
    parts.push(cur.name);
    cur = cur.parentId ? map.get(cur.parentId) : null;
  }
  return parts.reverse().join(" / ");
}

function reveal(id) {
  const map = byIdMap();
  let cur = map.get(id);
  while (cur && cur.parentId) {
    state.expanded.add(cur.parentId);
    cur = map.get(cur.parentId);
  }
}

document.querySelectorAll(".tab").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".panel").forEach((p) => p.classList.remove("active"));
    btn.classList.add("active");
    $("panel-" + btn.dataset.panel).classList.add("active");
  });
});

function userId() {
  return $("userId").value.trim() || "alice";
}

async function loadDirectory(preferId) {
  const data = await api("/api/org/directory");
  state.tree = data.tree || [];
  state.people = data.people || [];
  const flat = flatten(state.tree);
  if (state.expanded.size === 0) {
    for (const node of flat) state.expanded.add(node.id);
  }
  const fin = flat.find((node) => node.code === "FIN");
  if (fin && $("leaveOrgUnit")) $("leaveOrgUnit").value = fin.id;
  const stillThere = flat.some((node) => node.id === state.selectedId);
  const next = preferId || (stillThere ? state.selectedId : flat[0]?.id);
  renderTree();
  renderSearch();
  if (next) await selectUnit(next);
  else renderEmpty();
}

function renderEmpty() {
  state.ws = null;
  $("tree").innerHTML = `<div class="tree-empty"><strong>尚無部門</strong><p>建立根部門，或載入一份示範架構。</p></div>`;
  $("detail").innerHTML = `
    <div class="detail-empty">
      <h2>從組織樹開始</h2>
      <p>左側是部門層級。選中部門後，可維護崗位與人員。登入帳號稍後由 STS 開通。</p>
      <div class="actions">
        <button type="button" class="btn primary" id="emptyCreate">建立根部門</button>
        <button type="button" class="btn ghost" id="emptySeed">載入示範架構</button>
      </div>
    </div>`;
  $("emptyCreate").onclick = () => openUnitDialog(null);
  $("emptySeed").onclick = () => seedChart();
}

function renderTree() {
  const root = $("tree");
  root.innerHTML = "";
  if (!state.tree.length) {
    renderEmpty();
    return;
  }
  const q = state.q.trim().toLowerCase();
  const paint = (nodes, depth) => {
    for (const node of nodes) {
      const row = document.createElement("div");
      row.className = "tree-item";
      row.setAttribute("role", "treeitem");
      row.dataset.id = String(node.id);
      row.style.paddingLeft = 8 + depth * 14 + "px";
      if (node.id === state.selectedId) row.classList.add("is-selected");
      if (!node.enabled) row.classList.add("is-disabled");
      if (q && (node.name.toLowerCase().includes(q) || node.code.toLowerCase().includes(q))) {
        row.classList.add("is-hit");
      }
      const kids = node.children || [];
      const twist = document.createElement("button");
      twist.type = "button";
      twist.className = "twist" + (state.expanded.has(node.id) ? " is-open" : "");
      twist.setAttribute("aria-label", "展開或收合");
      twist.innerHTML = `<svg viewBox="0 0 20 20" width="14" height="14"><path fill="currentColor" d="M7 4l7 6-7 6V4z"/></svg>`;
      if (!kids.length) twist.disabled = true;
      twist.onclick = (ev) => {
        ev.stopPropagation();
        if (state.expanded.has(node.id)) state.expanded.delete(node.id);
        else state.expanded.add(node.id);
        renderTree();
      };
      const hit = document.createElement("button");
      hit.type = "button";
      hit.className = "node-hit";
      hit.innerHTML = `<span class="node-name">${esc(node.name)}</span><span class="node-count">${node.totalCount}</span>`;
      hit.onclick = () => selectUnit(node.id).catch((err) => toast(err.message, "err"));
      row.append(twist, hit);
      root.append(row);
      if (kids.length && state.expanded.has(node.id)) paint(kids, depth + 1);
    }
  };
  paint(state.tree, 0);
}

async function selectUnit(id) {
  state.selectedId = id;
  reveal(id);
  renderTree();
  const token = ++state.loadToken;
  const ws = await api("/api/org/units/" + id + "/workspace");
  if (token !== state.loadToken) return;
  state.ws = ws;
  renderDetail(ws);
  if (state.highlightKey) {
    const row = document.querySelector(`[data-user-key="${CSS.escape(state.highlightKey)}"]`);
    row?.scrollIntoView({ block: "nearest" });
  }
}

function renderDetail(ws) {
  const unit = ws.unit;
  const roles = ws.roles || [];
  const members = ws.members || [];
  $("detail").innerHTML = `
    <div class="crumb">${esc(breadcrumb(unit.id))}</div>
    <div class="detail-head">
      <div>
        <h1>${esc(unit.name)}</h1>
        <div class="meta">
          <span class="pill accent">${esc(unitTypeLabel(unit.unitType))}</span>
          <span class="pill">${esc(unit.code)}</span>
          <span class="pill">${esc(unit.path)}</span>
          <span class="pill">負責人 ${esc(ws.managerName || "未指定")}</span>
        </div>
      </div>
      <div class="head-actions">
        <button type="button" class="btn ghost" id="btnChild">新增下級</button>
        <button type="button" class="btn ghost" id="btnRole">新增崗位</button>
        <button type="button" class="btn primary" id="btnPerson">新增人員</button>
        <button type="button" class="btn ghost" id="btnEditUnit">編輯部門</button>
      </div>
    </div>
    ${unit.enabled ? "" : `<div class="banner">此部門已停用。可在編輯中重新啟用。</div>`}
    <div class="stats">
      <div class="stat"><b>${ws.directCount}</b><span>本級人員</span></div>
      <div class="stat"><b>${ws.totalCount}</b><span>含下級人員</span></div>
      <div class="stat"><b>${roles.length}</b><span>崗位</span></div>
    </div>
    <section class="section">
      <div class="section-head"><h2>崗位</h2></div>
      ${roles.length ? roleTable(roles, members) : `<div class="section-empty">此部門尚未設定崗位。</div>`}
    </section>
    <section class="section">
      <div class="section-head"><h2>人員</h2></div>
      ${members.length ? memberTable(members) : `<div class="section-empty">此部門尚無在職人員。</div>`}
    </section>`;
  $("btnChild").onclick = () => openUnitDialog(unit.id, unit.unitType === "DEPT" ? "TEAM" : "DEPT");
  $("btnRole").onclick = () => openRoleDialog(unit);
  $("btnPerson").onclick = () => openPersonDialog(unit, roles);
  $("btnEditUnit").onclick = () => openEditUnitDialog(ws);
  $("detail").querySelectorAll("[data-edit-person]").forEach((btn) => {
    btn.onclick = () => {
      const member = members.find((row) => String(row.membershipId) === btn.dataset.editPerson);
      if (!member) return;
      if (member.personId == null) openPersonDialog(unit, roles, member);
      else openEditPersonDialog(ws, member);
    };
  });
  $("detail").querySelectorAll("[data-open-account]").forEach((btn) => {
    btn.onclick = () => {
      const member = members.find((row) => String(row.membershipId) === btn.dataset.openAccount);
      if (member) openAccountDialog(unit, member);
    };
  });
}

function roleTable(roles, members) {
  const rows = roles
    .map((role) => {
      const count = members.filter((m) => m.orgRoleId === role.id).length;
      return `<tr><td>${esc(role.name)}</td><td class="num">${esc(role.code)}</td><td class="num">${count}</td></tr>`;
    })
    .join("");
  return `<table class="table"><thead><tr><th>崗位</th><th>編碼</th><th>在崗</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function memberTable(members) {
  const rows = members
    .map((member) => {
      const name = member.displayName || member.userKey;
      const account = member.accountLinked
        ? `<span class="pill ok">已關聯</span><small class="login-name">${esc(member.loginName || "")}</small>`
        : member.personId == null
          ? `<span class="pill warn">未開通</span>`
          : `<button type="button" class="btn small" data-open-account="${member.membershipId}">開通</button>`;
      const duty = member.primary ? `<span class="pill ok">主崗</span>` : `<span class="pill">兼職</span>`;
      const action = member.personId == null ? "建檔" : "編輯";
      return `<tr data-user-key="${esc(member.userKey)}">
        <td><div class="who"><span class="avatar" style="background:${avatarColor(name)}">${esc(initial(name))}</span>
          <span><strong>${esc(name)}</strong><small>${esc(member.email || member.employeeNo || "")}</small></span></div></td>
        <td class="num">${esc(member.employeeNo || "—")}</td>
        <td>${esc(member.roleName || "未定崗")}</td>
        <td>${duty}</td>
        <td>${account}</td>
        <td><div class="row-actions"><button type="button" class="btn small" data-edit-person="${member.membershipId}">${action}</button></div></td>
      </tr>`;
    })
    .join("");
  return `<table class="table"><thead><tr><th>人員</th><th>工號</th><th>崗位</th><th>任職</th><th>登入帳號</th><th></th></tr></thead><tbody>${rows}</tbody></table>`;
}

function typeOptions(selected) {
  return UNIT_TYPES.map(
    ([value, label]) =>
      `<option value="${value}"${value === selected ? " selected" : ""}>${label}</option>`
  ).join("");
}

function openDialog({ title, html, onSubmit, onDanger, saveLabel, onReady }) {
  $("dialogTitle").textContent = title;
  $("dialogBody").innerHTML = html;
  $("dialogSave").textContent = saveLabel || "儲存";
  const danger = $("dialogDanger");
  danger.hidden = !onDanger;
  danger.onclick = async () => {
    danger.disabled = true;
    try {
      await onDanger();
      $("dialog").close();
    } catch (err) {
      toast(err.message, "err");
    } finally {
      danger.disabled = false;
    }
  };
  $("dialogForm").onsubmit = async (ev) => {
    ev.preventDefault();
    const save = $("dialogSave");
    save.disabled = true;
    try {
      await onSubmit(new FormData(ev.target));
      $("dialog").close();
    } catch (err) {
      toast(err.message, "err");
    } finally {
      save.disabled = false;
    }
  };
  if (!$("dialog").open) $("dialog").showModal();
  if (onReady) onReady();
}

function openUnitDialog(parentId, defaultType) {
  const child = parentId != null;
  openDialog({
    title: child ? "新增下級部門" : "新增根部門",
    html: `
      <div class="form-grid">
        <label class="field">部門名稱<input name="name" required maxlength="40" /></label>
        <label class="field">編碼<input name="code" required maxlength="20" placeholder="例如 FIN" /></label>
        <label class="field full">類型<select name="unitType">${typeOptions(defaultType || (child ? "DEPT" : "COMPANY"))}</select></label>
      </div>`,
    onSubmit: async (fd) => {
      const name = String(fd.get("name") || "").trim();
      const code = String(fd.get("code") || "").trim();
      if (!name || !code) throw new Error("請填寫部門名稱與編碼");
      const created = await api("/api/org/units", {
        method: "POST",
        body: JSON.stringify({
          name,
          code,
          unitType: fd.get("unitType"),
          parentId: parentId,
        }),
      });
      toast("已建立「" + created.name + "」");
      if (parentId) state.expanded.add(parentId);
      await loadDirectory(created.id);
    },
  });
}

function openEditUnitDialog(ws) {
  const unit = ws.unit;
  const members = ws.members || [];
  const known = new Set(members.map((member) => member.userKey));
  const extra =
    unit.managerUserId && !known.has(unit.managerUserId)
      ? `<option value="${esc(unit.managerUserId)}" selected>${esc(ws.managerName || unit.managerUserId)}</option>`
      : "";
  const options = [`<option value="">未指定</option>`, extra]
    .concat(
      members.map((member) => {
        const selected = member.userKey === unit.managerUserId ? " selected" : "";
        const label = member.displayName || member.userKey;
        return `<option value="${esc(member.userKey)}"${selected}>${esc(label)}</option>`;
      })
    )
    .join("");
  openDialog({
    title: "編輯部門",
    html: `
      <div class="form-grid">
        <label class="field">部門名稱<input name="name" required value="${esc(unit.name)}" /></label>
        <label class="field">類型<select name="unitType">${typeOptions(unit.unitType)}</select></label>
        <label class="field">負責人<select name="managerUserId">${options}</select></label>
        <label class="field check"><input type="checkbox" name="enabled" ${unit.enabled ? "checked" : ""}/> 啟用中</label>
      </div>
      <p class="callout">編碼 ${esc(unit.code)} 建立後保持不變，組織路徑依編碼生成。</p>`,
    onSubmit: async (fd) => {
      await api("/api/org/units/" + unit.id, {
        method: "PUT",
        body: JSON.stringify({
          name: String(fd.get("name") || "").trim(),
          unitType: fd.get("unitType"),
          managerUserId: fd.get("managerUserId") || "",
          enabled: fd.get("enabled") === "on",
        }),
      });
      toast("已更新部門");
      await loadDirectory(unit.id);
    },
  });
}

function openRoleDialog(unit) {
  openDialog({
    title: "新增崗位 · " + unit.name,
    html: `
      <div class="form-grid">
        <label class="field">崗位名稱<input name="name" required maxlength="40" placeholder="例如 財務經理" /></label>
        <label class="field">編碼<input name="code" required maxlength="20" placeholder="例如 MANAGER" /></label>
      </div>`,
    onSubmit: async (fd) => {
      const name = String(fd.get("name") || "").trim();
      const code = String(fd.get("code") || "").trim();
      if (!name || !code) throw new Error("請填寫崗位名稱與編碼");
      await api("/api/org/roles", {
        method: "POST",
        body: JSON.stringify({ orgUnitId: unit.id, name, code }),
      });
      toast("已建立崗位");
      await selectUnit(unit.id);
    },
  });
}

function roleOptions(roles, selected) {
  const head = `<option value="">未定崗</option>`;
  return (
    head +
    (roles || [])
      .map(
        (role) =>
          `<option value="${role.id}"${role.id === selected ? " selected" : ""}>${esc(role.name)}</option>`
      )
      .join("")
  );
}

function openPersonDialog(unit, roles, existingMember) {
  const presetKey = existingMember?.userKey || "";
  openDialog({
    title: existingMember ? "補齊人員檔" : "新增人員 · " + unit.name,
    html: `
      <div class="form-grid">
        <label class="field">姓名<input name="displayName" required value="${esc(existingMember?.displayName || "")}" /></label>
        <label class="field">工號<input name="employeeNo" required value="${esc(existingMember?.employeeNo || "")}" /></label>
        <label class="field">郵箱<input name="email" type="email" value="${esc(existingMember?.email || "")}" /></label>
        <label class="field">電話<input name="phone" value="${esc(existingMember?.phone || "")}" /></label>
        <label class="field">崗位<select name="orgRoleId">${roleOptions(roles, existingMember?.orgRoleId)}</select></label>
        <label class="field check"><input type="checkbox" name="primary" checked /> 設為主崗</label>
        ${presetKey ? `<input type="hidden" name="userKey" value="${esc(presetKey)}" />` : ""}
      </div>
      <div class="callout"><strong>登入帳號</strong>由 STS 維護。人員檔建立後顯示為未開通，關聯 Users.Id 之後才會以該帳號登入。</div>`,
    onSubmit: async (fd) => {
      const displayName = String(fd.get("displayName") || "").trim();
      const employeeNo = String(fd.get("employeeNo") || "").trim();
      if (!displayName || !employeeNo) throw new Error("請填寫姓名與工號");
      const roleRaw = String(fd.get("orgRoleId") || "");
      await api("/api/org/people", {
        method: "POST",
        body: JSON.stringify({
          displayName,
          employeeNo,
          userKey: fd.get("userKey") || null,
          email: fd.get("email") || null,
          phone: fd.get("phone") || null,
          orgUnitId: unit.id,
          orgRoleId: roleRaw ? Number(roleRaw) : null,
          primary: fd.get("primary") === "on",
        }),
      });
      toast("已加入「" + displayName + "」");
      await loadDirectory(unit.id);
    },
  });
}

function openAccountDialog(unit, member) {
  openDialog({
    title: "登入帳號 · " + (member.displayName || ""),
    saveLabel: "開通新帳號",
    html: `
      <section>
        <h3 class="account-title">開通新帳號</h3>
        <div class="form-grid">
          <label class="field">登入名<input name="userName" required value="${esc(member.employeeNo || "")}" autocomplete="off" /></label>
          <label class="field">郵箱<input name="email" type="email" value="${esc(member.email || "")}" autocomplete="off" /></label>
          <label class="field full">初始密碼<input name="password" type="password" required minlength="8" autocomplete="new-password" /></label>
        </div>
        <p class="hint">登入名建議用工號。密碼只送往 STS，組織庫不保存。</p>
      </section>
      <section class="account-existing">
        <h3 class="account-title">關聯已有帳號</h3>
        <div class="row">
          <input id="idpQuery" placeholder="登入名或郵箱" autocomplete="off" />
          <button type="button" class="btn ghost" id="idpSearch">搜尋</button>
        </div>
        <div id="idpHits" class="idp-hits"></div>
      </section>`,
    onSubmit: async (fd) => {
      const userName = String(fd.get("userName") || "").trim();
      const password = String(fd.get("password") || "");
      if (!userName) throw new Error("請填寫登入名");
      if (password.length < 8) throw new Error("初始密碼至少 8 個字元");
      await api("/api/org/people/" + member.personId + "/account", {
        method: "POST",
        body: JSON.stringify({
          userName,
          email: String(fd.get("email") || "").trim(),
          password,
        }),
      });
      toast("已開通「" + userName + "」");
      await loadDirectory(unit.id);
    },
    onReady: () => {
      const search = async () => {
        const q = $("idpQuery").value.trim();
        if (!q) {
          toast("請輸入登入名或郵箱", "err");
          return;
        }
        const hits = await api("/api/org/idp/users?q=" + encodeURIComponent(q));
        const root = $("idpHits");
        root.innerHTML = "";
        if (!hits.length) {
          root.textContent = "沒有符合的帳號";
          return;
        }
        for (const hit of hits) {
          const btn = document.createElement("button");
          btn.type = "button";
          btn.className = "idp-hit";
          btn.disabled = hit.linked;
          btn.innerHTML = `<strong>${esc(hit.userName || "")}</strong><small>${esc(hit.email || "無郵箱")}${hit.linked ? " · 已關聯其他人員" : ""}</small>`;
          btn.onclick = async () => {
            try {
              await api("/api/org/people/" + member.personId + "/account/link", {
                method: "POST",
                body: JSON.stringify({ idpUserId: hit.id }),
              });
              toast("已關聯「" + (hit.userName || "") + "」");
              $("dialog").close();
              await loadDirectory(unit.id);
            } catch (err) {
              toast(err.message, "err");
            }
          };
          root.append(btn);
        }
      };
      $("idpSearch").onclick = () => search().catch((err) => toast(err.message, "err"));
      $("idpQuery").addEventListener("keydown", (ev) => {
        if (ev.key === "Enter") {
          ev.preventDefault();
          search().catch((err) => toast(err.message, "err"));
        }
      });
    },
  });
}

function openEditPersonDialog(ws, member) {
  const unit = ws.unit;
  openDialog({
    title: member.displayName || "編輯人員",
    html: `
      <div class="form-grid">
        <label class="field">姓名<input name="displayName" required value="${esc(member.displayName || "")}" /></label>
        <label class="field">工號<input name="employeeNo" required value="${esc(member.employeeNo || "")}" /></label>
        <label class="field">郵箱<input name="email" type="email" value="${esc(member.email || "")}" /></label>
        <label class="field">電話<input name="phone" value="${esc(member.phone || "")}" /></label>
        <label class="field">崗位<select name="orgRoleId">${roleOptions(ws.roles, member.orgRoleId)}</select></label>
        <label class="field check"><input type="checkbox" name="primary" ${member.primary ? "checked" : ""}/> 主崗</label>
      </div>
      <div class="callout"><strong>登入帳號</strong>${
        member.accountLinked
          ? "已關聯 STS，登入名 " + esc(member.loginName || "") + "。"
          : "尚未開通。在人員列表按「開通」，建立新帳號或關聯已有的 STS 用戶。"
      }</div>`,
    onSubmit: async (fd) => {
      const roleRaw = String(fd.get("orgRoleId") || "");
      await api("/api/org/people/" + member.personId, {
        method: "PUT",
        body: JSON.stringify({
          displayName: String(fd.get("displayName") || "").trim(),
          employeeNo: String(fd.get("employeeNo") || "").trim(),
          email: fd.get("email") || "",
          phone: fd.get("phone") || "",
          membershipId: member.membershipId,
          orgRoleId: roleRaw ? Number(roleRaw) : null,
          primary: fd.get("primary") === "on",
        }),
      });
      toast("已更新人員");
      await loadDirectory(unit.id);
    },
    onDanger: async () => {
      await api("/api/org/people/" + member.personId + "/disable", { method: "POST" });
      toast("已停用「" + (member.displayName || "") + "」");
      state.highlightKey = null;
      await loadDirectory(unit.id);
    },
  });
}

function renderSearch() {
  const box = $("searchResults");
  const q = state.q.trim().toLowerCase();
  box.innerHTML = "";
  if (!q) {
    box.hidden = true;
    return;
  }
  const units = flatten(state.tree)
    .filter((node) => node.name.toLowerCase().includes(q) || node.code.toLowerCase().includes(q))
    .slice(0, 6);
  const people = state.people
    .filter((person) => {
      const blob = [person.displayName, person.employeeNo, person.email, person.userKey]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return blob.includes(q);
    })
    .slice(0, 6);
  if (!units.length && !people.length) {
    box.hidden = true;
    return;
  }
  for (const node of units) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.innerHTML = `<span class="kind">部門</span><span>${esc(node.name)}</span><small>${esc(node.path)}</small>`;
    btn.onclick = () => {
      state.q = "";
      $("treeSearch").value = "";
      box.hidden = true;
      selectUnit(node.id).catch((err) => toast(err.message, "err"));
    };
    box.append(btn);
  }
  for (const person of people) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.innerHTML = `<span class="kind">人員</span><span>${esc(person.displayName)}</span><small>${esc(person.employeeNo || "")} · ${esc(person.unitName || "未任職")}</small>`;
    btn.onclick = () => {
      state.q = "";
      $("treeSearch").value = "";
      box.hidden = true;
      if (!person.orgUnitId) {
        toast("此人員尚未任職");
        return;
      }
      state.highlightKey = person.userKey;
      selectUnit(person.orgUnitId).catch((err) => toast(err.message, "err"));
    };
    box.append(btn);
  }
  box.hidden = false;
}

async function seedChart() {
  const btn = $("btnSeedChart");
  if (btn) btn.disabled = true;
  try {
    await api("/api/org/demo/seed-chart", { method: "POST", body: "{}" });
    toast("已載入示範架構");
    state.expanded.clear();
    await loadDirectory();
  } catch (err) {
    toast(err.message, "err");
  } finally {
    if (btn) btn.disabled = false;
  }
}

$("treeSearch").addEventListener("input", () => {
  state.q = $("treeSearch").value;
  renderTree();
  renderSearch();
});
document.addEventListener("click", (ev) => {
  if (!ev.target.closest(".search-wrap")) $("searchResults").hidden = true;
});
$("btnAddRoot").onclick = () => openUnitDialog(null);
$("btnReload").onclick = () => loadDirectory().catch((err) => toast(err.message, "err"));
$("btnSeedChart").onclick = () => seedChart();
$("dialogClose").onclick = () => $("dialog").close();
$("dialogCancel").onclick = () => $("dialog").close();

$("btnSeed").onclick = async () => {
  try {
    const result = await api("/api/workflow/demo/seed-leave", { method: "POST", body: "{}" });
    toast(result.notes.join(" · "));
    await loadDirectory();
    $("formDefs").textContent = JSON.stringify(await api("/api/forms/definitions"), null, 2);
  } catch (err) {
    toast(err.message, "err");
  }
};

$("btnSubmitLeave").onclick = async () => {
  try {
    const payload = {
      days: Number($("leaveDays").value),
      reason: $("leaveReason").value,
      amount: Number($("leaveAmount").value),
    };
    const out = await api("/api/forms/submit", {
      method: "POST",
      body: JSON.stringify({
        formCode: "leave",
        userId: userId(),
        orgUnitId: Number($("leaveOrgUnit").value),
        payloadJson: JSON.stringify(payload),
      }),
    });
    $("formOut").textContent = JSON.stringify(out, null, 2);
  } catch (err) {
    toast(err.message, "err");
  }
};

async function loadInbox() {
  const uid = $("approverId").value.trim();
  const tasks = await api("/api/self/inbox?userId=" + encodeURIComponent(uid));
  const root = $("inboxList");
  root.innerHTML = "";
  if (!tasks.length) {
    root.textContent = "目前沒有待辦";
    return;
  }
  for (const task of tasks) {
    const el = document.createElement("div");
    el.className = "task";
    el.innerHTML = `
      <div>
        <strong>#${task.id}</strong> ${esc(task.nodeKey)}
        <div class="meta">instance ${task.processInstanceId}</div>
      </div>
      <div>
        <button type="button" class="btn small" data-act="APPROVE">通過</button>
        <button type="button" class="btn ghost small" data-act="REJECT">駁回</button>
      </div>`;
    el.querySelectorAll("button").forEach((btn) => {
      btn.onclick = async () => {
        try {
          await api("/api/workflow/tasks/" + task.id + "/action", {
            method: "POST",
            body: JSON.stringify({ userId: uid, action: btn.dataset.act, remarks: "" }),
          });
          await loadInbox();
        } catch (err) {
          toast(err.message, "err");
        }
      };
    });
    root.append(el);
  }
}

$("btnInbox").onclick = () => loadInbox().catch((err) => toast(err.message, "err"));
$("btnMine").onclick = async () => {
  try {
    $("selfOut").textContent = JSON.stringify(
      await api("/api/self/mine?userId=" + encodeURIComponent(userId())),
      null,
      2
    );
  } catch (err) {
    toast(err.message, "err");
  }
};
$("btnOrgQuery").onclick = async () => {
  try {
    $("selfOut").textContent = JSON.stringify(
      await api("/api/self/org-forms?orgPathPrefix=" + encodeURIComponent($("orgPath").value)),
      null,
      2
    );
  } catch (err) {
    toast(err.message, "err");
  }
};
$("btnExport").onclick = async () => {
  try {
    const q =
      "/api/self/export?userId=" +
      encodeURIComponent(userId()) +
      "&orgPathPrefix=" +
      encodeURIComponent($("orgPath").value);
    $("selfOut").textContent = JSON.stringify(await api(q, { method: "POST" }), null, 2);
  } catch (err) {
    toast(err.message, "err");
  }
};

async function loadSession() {
  const me = await api("/api/me");
  const el = $("session");
  if (!el) return;
  if (!me.authenticated) {
    el.innerHTML = `<span class="session-name">本機示範</span>`;
    return;
  }
  el.innerHTML = `<span class="session-name">${esc(me.name || me.sub || "")}</span><a class="btn text" href="/logout">登出</a>`;
}

loadSession().catch(() => {});
loadDirectory().catch(() => renderEmpty());
api("/api/forms/definitions")
  .then((defs) => {
    $("formDefs").textContent = JSON.stringify(defs, null, 2);
  })
  .catch(() => {});
