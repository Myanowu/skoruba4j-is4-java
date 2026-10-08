# Publishing to GitHub

Repository name: **skoruba4j-is4-java**.

## Checklist

- [x] License file named `LICENSE` (Apache-2.0); `NOTICE` present
- [x] README first paragraph is the drop-in sentence (not “lightweight Keycloak”)
- [x] `docs/ABOUT.md` present
- [x] No connection strings, SMTP passwords, reCAPTCHA keys, or private database hosts in tracked files
- [x] No organization logos (product mark is `docs/brand/`)
- [x] No real production `client_id` values
- [x] DEMO password is documented as local-only (`docs/GETTING-STARTED.md`, `docs/FIRST-LOGIN.md`); SQLite seed creates it, there is no JSON importer
- [x] Local SQLite (`data/`, `*.sqlite`, `*.sqlite-wal`, `*.sqlite-shm`) is gitignored; a fresh clone creates its own file on first start
- [x] `.gitignore` includes `application-local.yml`, `*.p12`, `*.pfx`, `.env`, `config/idserver-local.yml`, `dist/`, `data/`, `*.sqlite*`, `.cursor/`
- [x] SECURITY.md disclosure path
- [ ] `docs/COMPATIBILITY.md` cells marked Y each have a test or a recorded e2e (review before tagging a release)
- [ ] English docs + `docs/zh/` stay in sync when behaviour changes

## Before the first push

1. `git status` must not list `application-local.yml`, `*.p12`, `config/client_secret_*.json`, `config/external-login-local.yml`, `config/idserver-local.yml`, `dist/`, or `.cursor/`.
2. Do not `git add -f` a gitignored file.
3. After the empty GitHub repository exists, enable private vulnerability reporting (see `SECURITY.md`).

SQLite DDL stays on the classpath (`com/myano/skoruba4j/domain/schema/sqlite-is4.sql`), described in `docs/schema/sqlite.md`. Do not duplicate it as a second script that can drift.
