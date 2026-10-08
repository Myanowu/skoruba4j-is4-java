# 关于 Skoruba4j

**Skoruba4j** 是产品名。它是面向已有 **IdentityServer4 + Skoruba Admin** 部署的 Java **STS（Security Token Service）** 和 **OpenID Provider**，用来做 drop-in 替换。

它**不是** Keycloak、Casdoor、MaxKey、Duende，也不是从零做的 IAM。若能改客户端 URL 并迁移用户，请用那些产品。

一句话：给已经为 IS4 配置数据付过成本、无法重开每个应用的团队，提供可替换的 Java STS。若这句话不成立，不要用本项目。更长的定位见 [WHY.md](WHY.md)。

## 真正的特点

Skoruba4j 保住的是旧栈已经付过的契约：

1. **同一套 SQL 表** — IS4 配置表（`Clients`、`ApiResources`、`PersistedGrants` 等）加 Identity 用户表（`table-style=skoruba` 为 `Users`，`aspnet` 为 `AspNetUsers`）。
2. **同一套对外路径** — `/connect/*` 与 `/.well-known/openid-configuration`，不是 Spring 默认的 `/oauth2/*`。
3. **同一个 access token `aud`** — JWT `aud` 是 `ApiResources.Name`，`IdentityServer4.AccessTokenValidation` 认这个。
4. **同一套密码** — 校验 ASP.NET Identity PBKDF2（v2/v3）；新哈希写 Identity v3。
5. **同一个 issuer 主机名** — 前面放反代，资源服务器不必改 Authority。

底层是 JDK 21 + Spring Boot 3.5 + Spring Authorization Server + JDBC（无 JPA）。SAS 只是实现；对外协议面是 IS4。

## 进程

- `skoruba4j-sts`：登录界面 + OpenID Provider / OAuth 2.0 发牌（IS4 `/connect/*`）
- `skoruba4j-admin`：同一批表的管理界面，公开页 `GET /about`
- `skoruba4j-admin-api`：同一批表的 REST
- `skoruba4j-console`：可选 Swing **Skoruba4j Control**（本机启停、健康检查、库探查：只读 + 紧急备份/恢复）；**不是** Admin 替代品

`/health` 只是进程探针，不是产品介绍页。

## 协议

状态与 [COMPATIBILITY.md](../COMPATIBILITY.md) 一致：**Y** 带测试交付；**P** 部分/计划；**N** 不做。

**OpenID Connect 1.0（Y）：** Discovery、JWKS、Authorization Code + PKCE、ID Token（JWT）、UserInfo、RP-Initiated Logout（`/connect/endsession`）。Introspection 为 **P**。

**OAuth 2.0（Y）：** `authorization_code` + PKCE、`refresh_token` / `offline_access`、`/connect/token`。**P：** revocation、`client_credentials`、Device Code、IS4 `delegation`、Resource Owner Password（客户端须允许 `password`）。**N：** implicit、mTLS。

**令牌：** JWT access token；`aud` = `ApiResources.Name`；`sub` = Identity 用户 Id。不把 reference token 当作 STS 现网存储。

Control → Settings → **Admin sign-in** 三选一（`idserver.admin.login-mode`：local / sts-password / sts-oidc）。sts-password 为本页表单 + STS password grant（无浏览器跳转，非跨应用 SSO）；sts-oidc 为浏览器 SSO。

新建空 SQLite 并 Initialize 后的首登账号见 [FIRST-LOGIN.md](FIRST-LOGIN.md)（`demo` / `Passw0rd!`，角色 `MyRole`）。Control Manage 也有 **First login…**。

英文：[../ABOUT.md](../ABOUT.md)。
