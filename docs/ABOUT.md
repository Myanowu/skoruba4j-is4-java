# About Skoruba4j

**Skoruba4j** is the product name. It is a Java **Security Token Service (STS)** and **OpenID Provider** that drop-in replaces an existing **IdentityServer4 + Skoruba Admin** deployment.

It is **not** Keycloak, Casdoor, MaxKey, Duende, or a greenfield IAM. If you can change client URLs and migrate users, use those products.

GitHub sidebar blurb (≤350 characters):

> Skoruba4j is a Java STS / OpenID Provider for existing IdentityServer4 + Skoruba tables and `/connect` clients. Same `aud` = ApiResources.Name and ASP.NET Identity hashes. Not Keycloak.

## One-sentence pitch

> Drop-in Java STS for people who already paid the cost of IS4 configuration data and cannot reopen every application.

If that sentence is false for a team, they should not use this project. Longer positioning: [WHY.md](WHY.md).

## What is actually special

Skoruba4j keeps the contracts the old stack already paid for:

1. **Same SQL tables** — IS4 configuration (`Clients`, `ApiResources`, `PersistedGrants`, …) plus Identity users (`table-style=skoruba` → `Users`, or `aspnet` → `AspNetUsers`).
2. **Same public paths** — `/connect/*` and `/.well-known/openid-configuration`, not Spring’s `/oauth2/*`.
3. **Same access-token audience** — JWT `aud` is `ApiResources.Name`, which `IdentityServer4.AccessTokenValidation` expects.
4. **Same passwords** — verify ASP.NET Identity PBKDF2 (v2/v3); new hashes write Identity v3.
5. **Same issuer hostname** — put a reverse proxy in front; resource servers keep their Authority.

Under the hood it is JDK 21 + Spring Boot 3.5 + Spring Authorization Server + JDBC (no JPA). SAS is an implementation detail; the published protocol surface is IS4.

## Processes

| Process | Role |
|---------|------|
| `skoruba4j-sts` | Login UI + OpenID Provider / OAuth 2.0 token service on IS4 `/connect/*` |
| `skoruba4j-admin` | Admin UI for the same tables. Public page: `GET /about` |
| `skoruba4j-admin-api` | REST over the same tables (`api/Clients`, `api/Users`, …) |
| `skoruba4j-console` | Optional Swing **Skoruba4j Control**; local start/stop, health, DB explore (read-only + emergency backup/restore) — not a replacement for Admin |

`GET /health` is a process probe (JSON). It is not a product page.

## Protocols

Status matches [COMPATIBILITY.md](COMPATIBILITY.md): **Y** ships with tests; **P** is partial / planned; **N** is out of scope.

### OpenID Connect 1.0

| Capability | Status |
|------------|--------|
| Discovery `/.well-known/openid-configuration` | Y |
| JWKS (`jwks_uri`, JWT `kid`) | Y |
| Authorization Code + PKCE | Y |
| ID Token (JWT) | Y |
| UserInfo `/connect/userinfo` | Y |
| RP-Initiated Logout `/connect/endsession` | Y |
| Token introspection `/connect/introspect` | Y |
| Session Management / Front-channel logout extras | not a Y cell |

### OAuth 2.0

| Grant / endpoint | Status |
|------------------|--------|
| `authorization_code` + PKCE | Y |
| `refresh_token` / `offline_access` | Y |
| `/connect/token` | Y |
| `/connect/revocation` | Y |
| `client_credentials` | Y |
| Device Authorization `/connect/deviceauthorization` | P |
| IS4 extension `grant_type=delegation` | P |
| Implicit | N |
| Resource Owner Password | Y | client must allow `password`; Admin `sts-password` uses it; `StsPasswordGrantTest` |
| mTLS `/connect/mtls/*` | N |

### Tokens and identity

| Behaviour | Status |
|-----------|--------|
| JWT access tokens | Y |
| `aud` = `ApiResources.Name` | Y |
| `sub` = Identity user id | Y |
| `scope` claim | Y |
| Opaque / reference tokens at the STS | N |
| Username or email login | Y |
| ASP.NET Identity password verify / v3 write | Y |

Admin sign-in is `idserver.admin.login-mode`: **local** (Users table), **sts-password** (Admin form → STS password grant, no browser redirect; not cross-app SSO), or **sts-oidc** (browser OIDC RP / SSO). Control → Settings → **Admin sign-in** picks one mode; legacy `oidc-enabled` still maps when `login-mode` is absent.

After a fresh SQLite **Initialize** (empty Users), sign in as `demo` / `Passw0rd!` (role `MyRole`). See [FIRST-LOGIN.md](FIRST-LOGIN.md).

## Locked contracts

- Paths stay `/connect/*`
- Access-token audience is `ApiResources.Name`
- Passwords verify ASP.NET Identity PBKDF2; new hashes write Identity v3
- Source of truth is the existing IS4 tables, not `oauth2_registered_client`
- JDK 21, Spring Boot 3.5, JDBC only
- License: Apache-2.0. This project is not an OpenID Certified implementation

## Public tree

The GitHub `oss` tree must clone, `mvn package`, and show `/health` plus discovery without private hosts or secrets. No production connection strings, SMTP, reCAPTCHA, organization logos, or real client ids.

Chinese: [zh/ABOUT.md](zh/ABOUT.md).
