# Skoruba4j（中文）

这是面向 **已有 IdentityServer4 + Skoruba 数据库与 `/connect` 客户端** 的 **非官方 Java STS / OpenID Provider（产品名 Skoruba4j）**，**不是** Keycloak / Casdoor / MaxKey 的替代品。若你能改客户端并迁移用户，请用那些产品。Maven 模块：`skoruba4j-sts`、`skoruba4j-admin`、`skoruba4j-admin-api`、`skoruba4j-console`。

当前状态：JDK 21 / Spring Boot 3.5。公开 profile `oss` 默认用内置 **SQLite**。下载后按 [GETTING-STARTED.md](GETTING-STARTED.md) 搭建；演示账号见 [FIRST-LOGIN.md](FIRST-LOGIN.md)。英文总览在仓库根 [README.md](../../README.md)。

英文主文档从仓库根 README 进入 `docs/`。本目录为中文镜像：

- [GETTING-STARTED.md](GETTING-STARTED.md) — 下载后搭建本机空库
- [WHY.md](WHY.md)
- [ABOUT.md](ABOUT.md)
- [FIRST-LOGIN.md](FIRST-LOGIN.md) — SQLite Initialize 后用谁登录 STS / Admin
- [MIGRATION.md](MIGRATION.md)（若尚未翻译，以英文 `docs/MIGRATION.md` 为准）

## 进程

公开演示端口：STS `5050`、Admin `6060`、Admin API `44302`。HTTPS（`5051` / `6061`）需要本机私钥库，不在本仓库里。可选 **Skoruba4j Control** 为 Swing 桌面程序（无监听端口）。JDK 21，Authorization Server + JDBC，默认 SQLite；已有 IS4 库可改 SQL Server / PostgreSQL / MySQL。三进程可分机发布；Control 在本机启停 jar，远程节点只做健康检查。

## 核心契约

- 路径 `/connect/*`
- `aud` = `ApiResources.Name`
- 密码 ASP.NET Identity PBKDF2
- 表名可 `skoruba`（`Users`）或 `aspnet`（`AspNetUsers`）

协议（已带测试）：OpenID Connect 1.0（Discovery、JWKS、Authorization Code + PKCE、ID Token、UserInfo、RP 登出、introspection）与 OAuth 2.0（`authorization_code`、`refresh_token`、revocation、`client_credentials`、resource owner password）。未完整：device code、IS4 `delegation`。不做：implicit、mTLS。详见 [ABOUT.md](ABOUT.md) / [COMPATIBILITY.md](../COMPATIBILITY.md)。
