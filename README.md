# Skoruba4j

[![CI](https://github.com/Myanowu/skoruba4j-is4-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Myanowu/skoruba4j-is4-java/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![JDK 21](https://img.shields.io/badge/JDK-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

<p align="left"><img src="docs/brand/skoruba4j.png" alt="Skoruba4j — identity STS mark (shield and keyhole)" width="96" height="96"></p>

Drop-in Java STS for teams that already paid for IdentityServer4 + Skoruba configuration data and cannot reopen every application.

**Skoruba4j** is an unofficial Java **Security Token Service** and **OpenID Provider** for an existing **IdentityServer4 + Skoruba** database. It keeps the SQL tables, ASP.NET Identity password hashes, `/connect/*` paths, and access-token `aud` = `ApiResources.Name`. IdentityServer4 is end of life; the supported .NET successor is Duende IdentityServer. This project is the JVM process for deployments that must stay on those contracts.

Current version: `0.1.0-SNAPSHOT` (JDK 21, Spring Boot 3.5, JDBC). The public `oss` profile needs no private hosts or secrets: `./mvnw -B package`, then `/health` and OpenID discovery on a bundled SQLite file.

Chinese overview: [docs/zh/README.md](docs/zh/README.md).

## Who it is for

Use Skoruba4j when all of these are true:

- Clients already call `/connect/authorize`, `/connect/token`, and the other IdentityServer4 routes.
- Resource servers expect JWT `aud` to be `ApiResources.Name` (the value `IdentityServer4.AccessTokenValidation` checks).
- User passwords are ASP.NET Identity PBKDF2 hashes that must keep working.
- The replacement process has to run on the JVM.

| If this is your situation | Look here instead |
|---------------------------|-------------------|
| You can stay on .NET | [Duende IdentityServer](https://duendesoftware.com/products/identityserver) |
| You can change Authority URLs, client registrations, and user hashes | [Keycloak](https://www.keycloak.org/) |
| You are starting a new IAM (social login, LDAP, a new schema) | Casdoor, MaxKey, or a new Keycloak realm |
| You control every client and can adopt Spring’s default paths and tables | [Spring Authorization Server](https://spring.io/projects/spring-authorization-server) by itself |

Longer positioning: [docs/WHY.md](docs/WHY.md). Product copy: [docs/ABOUT.md](docs/ABOUT.md).

## What it keeps

Skoruba4j is a compatibility layer on top of Spring Authorization Server. The published surface is the IdentityServer4 one:

1. **Same SQL tables** — IS4 configuration (`Clients`, `ApiResources`, `PersistedGrants`, …) plus Identity users. `idserver.identity.table-style=skoruba` uses `Users`; `aspnet` uses `AspNetUsers`. The source of truth stays those tables.
2. **Same paths** — discovery advertises `/connect/*`, including `/connect/authorize` and `/connect/token`.
3. **Same audience** — JWT `aud` is `ApiResources.Name`, joined through `ApiResourceScopes`. `sub` is the Identity user id.
4. **Same passwords** — verify ASP.NET Identity PBKDF2 (v2 and v3). New hashes are written as Identity v3.
5. **Same issuer hostname** — set `idserver.issuer-uri` to the public Authority. A reverse proxy can keep the hostname resource servers already trust.

## What you run

| Process | Default HTTP | Role |
|---------|--------------|------|
| `skoruba4j-sts` | [5050](http://127.0.0.1:5050/health) | Login UI and the OpenID Provider on `/connect/*` |
| `skoruba4j-admin` | [6060](http://127.0.0.1:6060/login) | Admin UI for the same tables (`/admin/clients`, `/admin/users`, …) |
| `skoruba4j-admin-api` | [44302](http://127.0.0.1:44302/health) | Skoruba-style JSON under `/api/**` (JWT). Browser console at `/` and `/ui/**` |
| `skoruba4j-console` | none (Swing) | Optional **Skoruba4j Control**: local start/stop, `/health`, read-only DB explore plus emergency backup/restore |

`skoruba4j-domain` and `skoruba4j-protocol` are libraries. You do not start them.

HTTPS on 5051 / 6061 is a local install option and needs a private keystore (`server.ssl.key-store`). That file is not in this repository. The public demo is HTTP.

Maven coordinates: `com.myano.skoruba4j`. YAML and environment variables stay `idserver.*` / `IDSERVER_*`.

## Requirements

- **JDK 21.** `JAVA_HOME` must point at that JDK.
- Git, and a network connection the first time Maven downloads dependencies.
- For a local try: nothing else. The first STS start creates `$IDSERVER_HOME/data/skoruba4j.sqlite`.
- For an existing deployment: JDBC access to SQL Server, PostgreSQL, or MySQL that already holds the IS4 tables.

## Quick start

Full walkthrough: [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) (中文 [docs/zh/GETTING-STARTED.md](docs/zh/GETTING-STARTED.md)).

STS, Admin, and Admin API must share one `IDSERVER_HOME`. Without it, each module creates its own SQLite file.

```bash
export JAVA_HOME=/path/to/jdk-21
export IDSERVER_HOME="$PWD"
./mvnw -pl skoruba4j-sts -am spring-boot:run
```

Windows Command Prompt: `set JAVA_HOME=C:\path\to\jdk-21` and `set IDSERVER_HOME=%CD%`, then `mvnw.cmd -pl skoruba4j-sts -am spring-boot:run`.

PowerShell:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:IDSERVER_HOME = (Get-Location).Path
.\mvnw.cmd -pl skoruba4j-sts -am spring-boot:run
```

The database file is `$IDSERVER_HOME/data/skoruba4j.sqlite` (gitignored). On a fresh file, when `Users` is empty, startup inserts a local DEMO account.

| Check | URL |
|-------|-----|
| Health | http://127.0.0.1:5050/health |
| Discovery | http://127.0.0.1:5050/.well-known/openid-configuration |
| Login | http://127.0.0.1:5050/login |

```bash
curl -sS http://127.0.0.1:5050/health
curl -sS http://127.0.0.1:5050/.well-known/openid-configuration
```

`/health` is JSON with `status`, `process`, `issuer`, and `database`. A good local start has `"status":"UP"` and `"database":"UP"`. Discovery should advertise `authorization_endpoint` ending in `/connect/authorize`, `token_endpoint` ending in `/connect/token`, and `jwks_uri` ending in `/.well-known/openid-configuration/jwks`.

Sign in as `demo` / `Passw0rd!` (role `MyRole`). That password exists only on this new local file. See [docs/FIRST-LOGIN.md](docs/FIRST-LOGIN.md).

Admin, in another terminal with the same `JAVA_HOME` and `IDSERVER_HOME`:

```bash
./mvnw -pl skoruba4j-admin -am spring-boot:run
```

Open http://127.0.0.1:6060/login and use the same DEMO user. After login the UI is http://127.0.0.1:6060/admin. The published default is `idserver.admin.login-mode=local`, so Admin checks the Users table itself.

Admin API (optional), same variables:

```bash
./mvnw -pl skoruba4j-admin-api -am spring-boot:run
```

Health: http://127.0.0.1:44302/health. The browser console is http://127.0.0.1:44302/. JSON callers use a Bearer token on `/api/**`.

Build everything the way CI does, without starting a server:

```bash
./mvnw -B test
```

`./mvnw -B package` also copies the runnable jars to `dist/lib` (gitignored).

Eclipse (m2e): import the root `pom.xml` as **Existing Maven Projects**. Do not use this folder as the Eclipse workspace. Bind **JavaSE-21** to a JDK 21 JRE, then run the module launch configs.

## Configuration

Keys live under `idserver.*`. Profiles: `oss` (tracked demo) and `local` (gitignored). STS, Admin, and Admin API import `config/idserver-local.yml` when that file exists.

Shown as you would write them in `config/idserver-local.yml`. Database, issuer, logout, and signing match `skoruba4j-sts` `application.yml`. `admin.role` and `admin.login-mode` match `skoruba4j-admin`. `allow-register` defaults to false in code.

```yaml
idserver:
  db:
    provider: sqlite    # sqlserver | postgresql | mysql for an existing IS4 database
    url: ${IDSERVER_DB_URL:jdbc:sqlite:${IDSERVER_HOME}/data/skoruba4j.sqlite?journal_mode=WAL&busy_timeout=8000}
    username: ${IDSERVER_DB_USERNAME:}
    password: ${IDSERVER_DB_PASSWORD:}
  identity:
    table-style: skoruba   # aspnet → AspNetUsers
  issuer-uri: http://127.0.0.1:5050
  login:
    resolution-policy: username   # email-or-username
    allow-register: false
  logout:
    end-session: compatible       # strict = expired JWT is dead, no refresh
  admin:
    role: MyRole
    login-mode: local             # sts-password | sts-oidc
  signing:
    jwk-file: secrets/sts-signing.jwk
```

Environment overrides for the database URL and credentials: `IDSERVER_DB_URL`, `IDSERVER_DB_USERNAME`, `IDSERVER_DB_PASSWORD`.

### Point at an existing IS4 database

Copy [config/idserver-local.yml.example](config/idserver-local.yml.example) to `config/idserver-local.yml` (gitignored). Set `idserver.db.provider` and `idserver.db.url`, and set `idserver.identity.table-style` to match the user tables you already have. Keep production passwords out of tracked files.

An existing database is not re-seeded when `Users` already has rows. Sign in with an account that already has `idserver.admin.role` (default `MyRole`). Set `idserver.issuer-uri` to the Authority your resource servers already use, usually behind a reverse proxy.

Empty `idserver.db.url` starts the process with no database. `/health` then reports `"database":"not-configured"`.

Full key list, ports, and session cookie names: [docs/CONFIG.md](docs/CONFIG.md). Cutover and rollback: [docs/MIGRATION.md](docs/MIGRATION.md), [docs/CUTOVER.md](docs/CUTOVER.md).

## Key endpoints

Issuer for the public demo is `http://127.0.0.1:5050`. Status matches [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md): **Y** ships with tests, **P** is partial, **N** is out of scope.

| Path | Purpose | Status |
|------|---------|--------|
| `GET /.well-known/openid-configuration` | OpenID discovery (advertises the paths below) | Y |
| `GET /.well-known/openid-configuration/jwks` | JWKS (`kid` matches the JWT) | Y |
| `GET /connect/authorize` | Authorization Code + PKCE | Y |
| `POST /connect/token` | Token endpoint | Y |
| `GET /connect/userinfo` | UserInfo | Y |
| `POST /connect/introspect` | Token introspection | Y |
| `POST /connect/revocation` | Revocation | Y |
| `GET /connect/endsession` | RP-initiated logout | Y |
| `POST /connect/deviceauthorization` | Device authorization | P |

`GET /health` is the process probe (`status`, `process`, `issuer`, `database`). `GET /login` is the username or email form; `ReturnUrl` resumes `/connect/authorize`.

Grants on `/connect/token`: `authorization_code` (Y), `refresh_token` (Y), `client_credentials` (Y), resource-owner `password` (Y, only if that client allows it), `delegation` (P), device code (P). Implicit and mTLS are out of scope.

Admin API (Bearer token, role claim equal to `idserver.admin.role`):

| Base path | What it covers |
|-----------|----------------|
| `/api/Clients` | Clients, secrets, properties, claims |
| `/api/ApiResources` | API resources (`aud`) |
| `/api/ApiScopes` | API scopes |
| `/api/IdentityResources` | Identity resources |
| `/api/Users` | Users, roles, claims, password |
| `/api/Roles` | Roles |
| `/api/PersistedGrants` | List and revoke |

There is no “every user is an administrator” switch. Details: [docs/ADMIN-API.md](docs/ADMIN-API.md). Protocol rules (`aud`, `sub`, client-secret hashing): [docs/PROTOCOL.md](docs/PROTOCOL.md).

## Status and limitations

Work that is in place and tested includes discovery, JWKS, authorization code + PKCE, ID tokens, UserInfo, introspection, revocation, refresh tokens, client credentials, the resource-owner password grant, JWT access tokens with `aud` = `ApiResources.Name`, ASP.NET Identity password verify/write, TOTP at `/login/2fa` when `TwoFactorEnabled` is set and an authenticator key exists, and the Admin UI plus Admin API. SQL Server, PostgreSQL, MySQL, and the bundled SQLite file are JDBC targets. The matrix is [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

Read these limits before a production cutover:

- This project is **not** an OpenID Certified implementation.
- Version is **0.1.0-SNAPSHOT**. [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) is the contract for what is done.
- Implicit flow and `/connect/mtls/*` are out of scope. Device authorization and the IS4 `delegation` grant are partial.
- Opaque / reference tokens are not issued. Access tokens are JWTs.
- Existing ASP.NET Data Protection cookies are not accepted. Users sign in again at cutover. C# refresh tokens are not reused (see [docs/CUTOVER.md](docs/CUTOVER.md)).
- Self-registration (`/register`) is off unless `idserver.login.allow-register=true`.
- External login (Microsoft, Google, WeChat, WhatsApp) is partial and needs secrets outside this repository. GitHub login is not implemented.
- Forgot-password (`/forgot-password`) does not send mail when `idserver.smtp.host` is empty, and it does not reveal whether the account exists.
- The Swing console is for health, logs, and emergency data fix. Day-to-day client and user edits belong in Admin or the Admin API.
- The public tree has no production connection strings, SMTP passwords, TLS keystores, or organization client ids.

## Documentation

| Doc | Purpose |
|-----|---------|
| [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) | Empty checkout to a running STS and Admin |
| [docs/FIRST-LOGIN.md](docs/FIRST-LOGIN.md) | DEMO user and Admin sign-in modes |
| [docs/CONFIG.md](docs/CONFIG.md) | Configuration keys, profiles, ports |
| [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) | Endpoint, grant, and table matrix |
| [docs/PROTOCOL.md](docs/PROTOCOL.md) | `/connect` mapping and `aud` rules |
| [docs/SCHEMA.md](docs/SCHEMA.md) | Table names (`skoruba` vs `aspnet`) |
| [docs/PASSWORD.md](docs/PASSWORD.md) | ASP.NET Identity PBKDF2 |
| [docs/ADMIN-API.md](docs/ADMIN-API.md) | Admin API path map |
| [docs/MIGRATION.md](docs/MIGRATION.md) | Move an existing IS4 database |
| [docs/CUTOVER.md](docs/CUTOVER.md) | DNS switch, dual-run, rollback |
| [docs/WHY.md](docs/WHY.md) | Positioning vs Duende, Keycloak, and plain SAS |
| [docs/ABOUT.md](docs/ABOUT.md) | Product about (also served at Admin `/about`) |
| [docs/zh/README.md](docs/zh/README.md) | 中文总览 |

## Contributing

Issues and pull requests are welcome for compatibility fixes that ship with tests, SQL dialect bugs (SQL Server, PostgreSQL, MySQL), password-hash edge cases, Admin API path parity, and docs in English or `docs/zh/`.

A compatibility cell in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) is marked **Y** only with a test. Protocol or `aud` changes update [docs/PROTOCOL.md](docs/PROTOCOL.md) in the same change. Keep the `oss` tree free of real credentials.

Out of scope: turning this into a general IAM (SAML, LDAP, a social IdP catalog), copying Skoruba Razor views, and adding organization-specific clients or secrets.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a PR. Report credential theft, token forgery, or auth bypass in private; see [SECURITY.md](SECURITY.md).

## License

[Apache-2.0](LICENSE). Copyright and third-party notices: [NOTICE](NOTICE).

This repository does not copy Skoruba Razor views or IdentityServer4 C# sources. Table and protocol behavior is aligned with those products so existing databases and `/connect` clients can keep working.
