# First login after SQLite Initialize

STS, Admin, and Admin API run the same SQLite initializer on startup when `idserver.db.provider=sqlite`. Control **Manage → Initialize** does the same thing. If `Users` is empty, Skoruba4j inserts a DEMO identity store.

| Field | Value |
|-------|--------|
| Username | `demo` |
| Password | `Passw0rd!` |
| Role | `MyRole` |
| Admin client | `skoruba4j-admin` (grants: `authorization_code`, `password`) |

## Who signs in where

- **STS** — open the STS login page; sign in as `demo` / `Passw0rd!`.
- **Admin** — Control → Settings → **Admin sign-in** picks **one** mode (`idserver.admin.login-mode`):
  - **Local password** — Admin authenticates against the Users table (`demo` / `Passw0rd!`).
  - **STS password (Admin page, no redirect)** — same Admin form; Admin server calls STS `grant_type=password`. Browser never opens STS login; this does **not** create cross-app SSO.
  - **STS OIDC redirect (SSO)** — browser redirect to STS; SSO with other OIDC apps (same model as C# Skoruba Admin).
- Data pages require the role named by `idserver.admin.role` (default `MyRole`).

`issuer-uri` must match the STS listen URL (HTTPS Control install: `https://localhost:5051`). Redirect URIs for `skoruba4j-admin` include `https://localhost:6061/signin-oidc` and `http://127.0.0.1:6060/signin-oidc`. Restart Admin after changing sign-in mode.

## Existing databases

Initialize does **not** rewrite existing Clients. For **STS password** mode, add grant type `password` on the Admin client (Admin UI → Clients, or SQL). Fresh Initialize already seeds `authorization_code` + `password` on `skoruba4j-admin`.

## When DEMO is not created

Initialize only seeds when **Users is empty**. If the table already had rows, use an existing user that already has the configured admin role. Control Manage → **First login…** repeats this help.

See also [schema/sqlite.md](schema/sqlite.md). Chinese: [zh/FIRST-LOGIN.md](zh/FIRST-LOGIN.md).
