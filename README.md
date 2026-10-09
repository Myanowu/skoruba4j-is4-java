# Skoruba4j

<p align="left"><img src="docs/brand/skoruba4j.png" alt="Skoruba4j — identity STS mark (shield and keyhole)" width="96" height="96"></p>

**Skoruba4j** is an unofficial Java **STS / OpenID Provider** for existing **IdentityServer4 + Skoruba** databases and `/connect` clients. It keeps the old tables, password hashes, `/connect/*` paths, and access-token `aud` = `ApiResources.Name`. It is **not** a Keycloak, Casdoor, or MaxKey alternative. If you can change clients and migrate users, use those products.

Maven modules: `skoruba4j-sts`, `skoruba4j-admin`, `skoruba4j-admin-api`, `skoruba4j-console`.

**Status:** JDK 21 / Spring Boot 3.5. The public `oss` profile uses bundled SQLite and can serve `/health` and OpenID discovery after `mvn package`. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) (cells marked Y must ship with tests).

## Why this exists

IdentityServer4 is end-of-life. The official .NET path is Duende. Teams that must run on the JVM, but already have:

- IS4 Entity Framework tables (`Clients`, `ApiResources`, `PersistedGrants`, …)
- ASP.NET Identity password hashes
- apps hard-coded to `/connect/authorize`, `/connect/token`, and `aud` = `ApiResource.Name`

…need a compatible Java process, not a new IAM product.

Start here: [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md). Chinese: [docs/zh/GETTING-STARTED.md](docs/zh/GETTING-STARTED.md), overview [docs/zh/README.md](docs/zh/README.md).

## Runtime

| Process | HTTP (public demo) | Role |
|---------|--------------------|------|
| `skoruba4j-sts` | 5050 | Login UI + OpenID Provider / OAuth 2.0 on `/connect/*` |
| `skoruba4j-admin` | 6060 | Admin UI |
| `skoruba4j-admin-api` | 44302 | Admin REST (`api/Clients`, `api/Users`, …) |
| `skoruba4j-console` (optional) | Swing desktop | **Skoruba4j Control**: health, logs, identity-store tools, local start/stop |

HTTPS (5051 / 6061) is a local install option and needs a private keystore. That keystore is not in this repository.

`skoruba4j-console` is Swing, not a web app. The window title is **Skoruba4j Control**. The three processes stay independently publishable; Control starts jars on the same machine and polls `/health` for remote nodes.

**Protocols (Y, with tests):** OpenID Connect 1.0 (Discovery, JWKS, Authorization Code + PKCE, ID Token, UserInfo, RP logout, introspection) and OAuth 2.0 (`authorization_code`, `refresh_token`, revocation, `client_credentials`, resource owner password). JWT access tokens use `aud` = `ApiResources.Name`. Partial: device code, IS4 `delegation`. Not in scope: implicit, mTLS. Full matrix: [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md), product copy: [docs/ABOUT.md](docs/ABOUT.md).

Stack: **JDK 21**, Spring Boot 3.5.x, Spring Authorization Server, **JDBC only** (no JPA). Default database is bundled **SQLite** (`data/skoruba4j.sqlite`). SQL Server, PostgreSQL, and MySQL are for an existing IS4 database.

## Quick start

Step-by-step setup, including a shared empty SQLite file that is not committed: [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md).

`JAVA_HOME` must be JDK 21. From the repository root, set one home for every process. Without it, each module creates its own database.

```bash
export JAVA_HOME=/path/to/jdk-21
export IDSERVER_HOME="$PWD"
./mvnw -pl skoruba4j-sts -am spring-boot:run
```

Windows Command Prompt: `set IDSERVER_HOME=%CD%` then `mvnw.cmd -pl skoruba4j-sts -am spring-boot:run`.

The database file is `$IDSERVER_HOME/data/skoruba4j.sqlite`. It is created on first start and listed in `.gitignore`.

- Health: `http://127.0.0.1:5050/health`
- Discovery: `http://127.0.0.1:5050/.well-known/openid-configuration`
- Login: `http://127.0.0.1:5050/login` (`demo` / `Passw0rd!` on a fresh file)

Admin, in another terminal with the same variables: `./mvnw -pl skoruba4j-admin -am spring-boot:run`, then `http://127.0.0.1:6060/login`.

Eclipse (m2e): import the root `pom.xml` as **Existing Maven Projects**. Do not use this folder as the Eclipse workspace. Bind **JavaSE-21** to a JDK 21 JRE, then run the `skoruba4j-sts` / `skoruba4j-admin` / `skoruba4j-admin-api` / `skoruba4j-console` launch configs.

## Documentation

| Doc | Purpose |
|-----|---------|
| [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) | Empty checkout to a running STS and Admin |
| [docs/ABOUT.md](docs/ABOUT.md) | Product about (GitHub + in-app `/about`) |
| [docs/WHY.md](docs/WHY.md) | Positioning vs Keycloak / Duende / MaxKey |
| [docs/MIGRATION.md](docs/MIGRATION.md) | Step-by-step cutover from Skoruba/IS4 |
| [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) | Endpoint / grant / table matrix |
| [docs/PROTOCOL.md](docs/PROTOCOL.md) | `/connect` mapping and `aud` rules |
| [docs/SCHEMA.md](docs/SCHEMA.md) | Table names (`skoruba` vs `aspnet`) |
| [docs/PASSWORD.md](docs/PASSWORD.md) | ASP.NET Identity PBKDF2 |
| [docs/ADMIN-API.md](docs/ADMIN-API.md) | Admin API path map |
| [docs/CONFIG.md](docs/CONFIG.md) | Configuration keys and profiles |
| [docs/CUTOVER.md](docs/CUTOVER.md) | DNS switch, dual-run, rollback |
| [docs/PUBLISHING.md](docs/PUBLISHING.md) | GitHub release checklist |
| [docs/IMPLEMENTATION-PLAN.md](docs/IMPLEMENTATION-PLAN.md) | Module layout and build order |
| [docs/FIRST-LOGIN.md](docs/FIRST-LOGIN.md) | DEMO user after a fresh SQLite database |

## Not in this repository

- Production connection strings, SMTP, reCAPTCHA, client secrets, or TLS keystores
- Organization-specific client ids
- Copies of Skoruba Razor views

License: [Apache-2.0](LICENSE). This project is **not** an OpenID Certified implementation.
