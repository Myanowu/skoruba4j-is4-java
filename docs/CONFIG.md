# Configuration

## Spring profiles

| Profile | Use | Git |
|---------|-----|-----|
| `oss` | Demo clients, local SQL or Docker, documented ports | yes |
| `local` | Private connection strings (machine-specific) | **no** (`application-local.yml` gitignored) |

## Keys

```yaml
idserver:
  db:
    provider: sqlite      # default install; sqlserver | postgresql | mysql for existing IS4
    url: ${IDSERVER_DB_URL:jdbc:sqlite:${IDSERVER_HOME}/data/skoruba4j.sqlite?journal_mode=WAL&busy_timeout=8000}
    username: ${IDSERVER_DB_USERNAME:}
    password: ${IDSERVER_DB_PASSWORD:}
  identity:
    table-style: skoruba  # aspnet
  login:
    resolution-policy: username  # email-or-username
  logout:
    end-session: compatible  # compatible = IS4 clients (refresh allowed); strict = expired JWT is dead, no refresh, client must re-login
  issuer-uri: https://localhost:5051
  admin:
    role: MyRole          # must exist in Roles; do not auto-inject
```

Default install points at bundled SQLite (`${IDSERVER_HOME}/data/skoruba4j.sqlite`). Packaged `dist/config/idserver-local.yml` is loaded last (`spring.config.additional-location`) so it overrides profile yaml, including a Spring `local` profile’s JDBC keys. Private SQL Server (or other) URLs stay in gitignored `application-local.yml` only when you put them back in the install overlay or env. Do not commit Azure hosts or passwords.

Shared local overlay: copy `config/idserver-local.yml.example` to gitignored `config/idserver-local.yml`, or save it from the Swing **Settings** tab in `skoruba4j-console`. The console can start/stop **local** fat jars (`mvn package` first); remote nodes are health-only.

JDBC maps existing IS4 / Identity tables. Admin UI (`http://127.0.0.1:6060/admin`) lists and edits Clients, Users, Roles, API resources, and grants. Bare `application.yml` uses `idserver.admin.login-mode=local` (no STS required). Control / `idserver-local.yml` defaults `login-mode=sts-password` (Admin form → STS password grant); choose `sts-oidc` for browser SSO. Legacy `oidc-enabled` still maps when `login-mode` is absent. Set `idserver.db.url` to empty to start without a database (`/health` shows `database: not-configured`).

Connection string names (Skoruba-compatible, may point at one database):

- `ConfigurationDbConnection`
- `PersistedGrantDbConnection`
- `IdentityDbConnection`
- optional: `AdminLogDbConnection`, `AdminAuditLogDbConnection`

## Ports

| App | HTTP | HTTPS | Session cookie |
|-----|------|-------|----------------|
| STS | 5050 | 5051 | `SKORUBA4J_STS_SESSION` |
| Admin | 6060 | 6061 | `SKORUBA4J_ADMIN_SESSION` |
| Admin API | | 44302 | (JWT, no browser session) |
| Console (optional) | Swing desktop (no listen port) | | |

Browser cookies are not port-specific. STS and Admin on `localhost` cannot both use `JSESSIONID`, or login overwrites the other app's session and authorize bounces back to `/login`.

## Libraries (allow-list)

- `spring-boot-starter-oauth2-authorization-server` (STS)
- `spring-boot-starter-oauth2-resource-server` (Admin API)
- `spring-boot-starter-oauth2-client` (Admin)
- `spring-boot-starter-jdbc`
- `mssql-jdbc` / `postgresql` / `mysql-connector-j` / `sqlite-jdbc` (one per provider)
- optional `jakarta.mail`

Everything else (reCAPTCHA, TOTP, health) uses JDK APIs. `GET /health` is a plain MVC controller, not Actuator.

`idserver.db.provider=sqlite` is the **bundled first-install database**: file `${IDSERVER_HOME}/data/skoruba4j.sqlite`. For a packaged build, **IDSERVER_HOME is the `dist/` directory** (the same tree the installer copies). YAML stores `jdbc:sqlite:${IDSERVER_HOME}/data/skoruba4j.sqlite?journal_mode=WAL&busy_timeout=8000`. `dist/cmd/env.cmd` sets `IDSERVER_HOME` to the parent of `cmd/` (that `dist/` or install folder). Console Start uses the same home. `mvn package` copies gitignored `data/skoruba4j.sqlite*` into `dist/data` when present. Empty `url` still means no database.

The public demo listens on HTTP 5050 / 6060 / 44302. HTTPS 5051 / 6061 needs a private keystore (`server.ssl.key-store`); that file is gitignored and is not required for `/health` or discovery.

STS issuer for local HTTP: `idserver.issuer-uri` (default `http://127.0.0.1:5050`). Discovery: `http://127.0.0.1:5050/.well-known/openid-configuration`. Interactive login is `GET /login` (username or email). Logout: form `GET/POST /logout` and OIDC `/connect/endsession`. `idserver.logout.end-session` is one switch for logout **and** token lifetime: `compatible` (default, IS4 / refresh allowed) or `strict` (expired JWT is rejected with no clock skew, refresh tokens are not issued, client must re-login).
