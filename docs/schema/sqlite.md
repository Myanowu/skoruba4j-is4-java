# SQLite demo DDL (embedded first install)

Used when `idserver.db.provider=sqlite`. File: `${IDSERVER_HOME}/data/skoruba4j.sqlite`. For a packaged build, IDSERVER_HOME is `dist/` (the installer copies that tree). `mvn package` copies gitignored `data/skoruba4j.sqlite*` into `dist/data` when that file exists.

The runnable script is on the classpath: `com/myano/skoruba4j/domain/schema/sqlite-is4.sql` plus Identity tables created in Java (`Users` or `AspNetUsers` from `table-style`). `CREATE TABLE IF NOT EXISTS` only. **Do not run against a production IS4 database.**

WAL + `busy_timeout=8000` so STS, Admin, and Admin API can share the file. `sqlite-jdbc` ships platform natives inside the jar.

DEMO seed (empty Users table only): user `demo` / `Passw0rd!` (Identity v3 hash), role `MyRole`, identity resources `openid`/`profile`/`email`/`roles`, client `skoruba4j-admin` (PKCE, authorization_code).

How to sign in to STS / Admin after Initialize: [../FIRST-LOGIN.md](../FIRST-LOGIN.md) (中文 [../zh/FIRST-LOGIN.md](../zh/FIRST-LOGIN.md)).
