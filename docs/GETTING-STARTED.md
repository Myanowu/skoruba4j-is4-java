# Getting started

This is the short path for a new checkout. You do not install a database server. The first start creates an empty SQLite file on your machine and a DEMO user. That file stays local and is gitignored.

Product name: **Skoruba4j**. Suggested GitHub repository name: **skoruba4j-is4-java**.

## Names you will see

| Where | Name | Meaning |
|-------|------|---------|
| Product, window title, docs | Skoruba4j | Java STS for an existing IdentityServer4 + Skoruba database |
| Maven modules | `skoruba4j-sts`, `skoruba4j-admin`, `skoruba4j-admin-api`, `skoruba4j-console` | The three processes, plus an optional Swing console |
| Java package | `com.myano.skoruba4j` | Build coordinates |
| YAML and environment | `idserver.*`, `IDSERVER_*` | Configuration prefix. It is not a second product name |
| SQLite file | `data/skoruba4j.sqlite` | Created at runtime under `IDSERVER_HOME` |

`skoruba4j-domain` and `skoruba4j-protocol` are libraries. You do not start them.

## What you need

- JDK **21**. `JAVA_HOME` must point at that JDK, not an older Java on `PATH`.
- Git, and a network connection the first time Maven downloads dependencies.

## 1. Shared home

All three processes must use the **same** `IDSERVER_HOME`. If you skip this, each Maven module uses its own working directory and creates a separate database.

From the repository root:

```bash
export JAVA_HOME=/path/to/jdk-21
export IDSERVER_HOME="$PWD"
```

Windows Command Prompt:

```bat
set JAVA_HOME=C:\path\to\jdk-21
set IDSERVER_HOME=%CD%
```

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:IDSERVER_HOME = (Get-Location).Path
```

Keep that variable in every terminal you use to start a process.

## 2. Start STS

```bash
./mvnw -pl skoruba4j-sts -am spring-boot:run
```

Windows: `mvnw.cmd -pl skoruba4j-sts -am spring-boot:run`.

The first start writes:

`%IDSERVER_HOME%/data/skoruba4j.sqlite`

and, because `Users` is empty, the DEMO account below. Leave this terminal running.

| Check | URL |
|-------|-----|
| Health | http://127.0.0.1:5050/health |
| Discovery | http://127.0.0.1:5050/.well-known/openid-configuration |
| Login | http://127.0.0.1:5050/login |

`/health` should include `"database":"UP"`.

Sign in as `demo` / `Passw0rd!`. That password is for this local file only.

## 3. Start Admin

New terminal, same `JAVA_HOME` and `IDSERVER_HOME`:

```bash
./mvnw -pl skoruba4j-admin -am spring-boot:run
```

Open http://127.0.0.1:6060/login and use the same DEMO user. After login the UI is http://127.0.0.1:6060/admin.

The published default is `idserver.admin.login-mode=local`: Admin checks the Users table itself. You do not need the STS browser redirect for this first run. STS should still be the process that minted the shared file if you followed the order above; Admin reuses it.

## 4. Admin API (optional)

New terminal, same variables:

```bash
./mvnw -pl skoruba4j-admin-api -am spring-boot:run
```

Health: http://127.0.0.1:44302/health

## Stop and start over

Stop each process with Ctrl+C. Delete these files if they exist:

- `$IDSERVER_HOME/data/skoruba4j.sqlite`
- `$IDSERVER_HOME/data/skoruba4j.sqlite-wal`
- `$IDSERVER_HOME/data/skoruba4j.sqlite-shm`

Start STS again. A new empty file and a new DEMO user are created.

Do not commit those files. `.gitignore` already excludes `data/`, `*.sqlite`, `*.sqlite-wal`, and `*.sqlite-shm`. `git status` must not list them. Do not `git add -f` them.

## Point at an existing IS4 database

Leave the SQLite file out of Git either way. For SQL Server, PostgreSQL, or MySQL, copy [../config/idserver-local.yml.example](../config/idserver-local.yml.example) to `config/idserver-local.yml` (gitignored) and set `idserver.db.provider` plus `idserver.db.url`. Keys are listed in [CONFIG.md](CONFIG.md). Do not put production passwords in a tracked file.

An existing database is not re-seeded when `Users` already has rows. Use an account that already has the admin role (`MyRole` unless you changed `idserver.admin.role`).

## Where to read next

| Doc | Use it for |
|-----|------------|
| [FIRST-LOGIN.md](FIRST-LOGIN.md) | DEMO user and Admin sign-in modes |
| [CONFIG.md](CONFIG.md) | Ports, profiles, database keys |
| [ABOUT.md](ABOUT.md) | What this product is |
| [COMPATIBILITY.md](COMPATIBILITY.md) | Which protocol pieces are done |
| [MIGRATION.md](MIGRATION.md) | Cut over from a running IS4 database |

Chinese: [zh/GETTING-STARTED.md](zh/GETTING-STARTED.md).
