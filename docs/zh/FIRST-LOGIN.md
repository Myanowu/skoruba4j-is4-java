# SQLite Initialize 之后怎么登录

`idserver.db.provider=sqlite` 时，STS、Admin、Admin API 在启动时都会做同一套 SQLite 初始化。Control 的 **Manage → Initialize** 做的是同一件事。`Users` 为空时写入 DEMO 身份数据。

| 项 | 值 |
|----|-----|
| 用户名 | `demo` |
| 密码 | `Passw0rd!` |
| 角色 | `MyRole` |
| Admin 客户端 | `skoruba4j-admin`（授权类型含 `authorization_code`、`password`） |

## 各进程用谁登录

- **STS** — 打开 STS 登录页，用 `demo` / `Passw0rd!`。
- **Admin** — Control → Settings → **Admin sign-in** 三选一（`idserver.admin.login-mode`，登录页只显示当前模式）：
  - **Local password** — Admin 直接校验 Users 表（`demo` / `Passw0rd!`）
  - **STS password（本页表单、不跳转）** — 同一 Admin 表单；Admin 服务端用 STS `grant_type=password`。浏览器不打开 STS 登录页，不会形成跨应用 SSO
  - **STS OIDC redirect（SSO）** — 浏览器跳转到 STS，与其它 OIDC 应用 SSO（同 C# Skoruba Admin）
- 进入数据页需要拥有 `idserver.admin.role` 配置的角色（默认 `MyRole`）。

`issuer-uri` 必须与 STS 监听地址一致（HTTPS Control：`https://localhost:5051`）。改登录方式后请重启 Admin。

## 已有数据库

Initialize **不会**改写已有 Clients。若要用 **STS password**，请在 Admin 客户端上增加 `password` 授权类型（Admin UI → Clients，或 SQL）。全新 Initialize 已给 `skoruba4j-admin` 种子 `authorization_code` + `password`。

## 什么时候不会创建 demo

只有 **Users 为空** 时才会 seed。若表里已有用户，Initialize 不会新建 demo，请用已有且带管理角色的账号。Control Manage 底部的 **First login…** 会再次显示这段说明。

英文：[../FIRST-LOGIN.md](../FIRST-LOGIN.md)。
