# Try Skoruba4j

This is a local demo of the public `oss` profile. It starts the STS (and, optionally, Admin) on **bundled SQLite**. The first start creates the database file and a DEMO user. Nothing here is a production IdentityServer4 database, and the password below is **demo-only**.

What this demo actually serves:

| Check | URL | What you get |
|-------|-----|----------------|
| Health | http://127.0.0.1:5050/health | JSON. `database` is `UP` after SQLite opens |
| Discovery | http://127.0.0.1:5050/.well-known/openid-configuration | OpenID Provider metadata. Paths are `/connect/*` |
| JWKS | http://127.0.0.1:5050/.well-known/openid-configuration/jwks | Signing keys created on first start under `IDSERVER_HOME/secrets/` |
| Login | http://127.0.0.1:5050/login | Username `demo`, password `Passw0rd!` |
| Signed-in home | http://127.0.0.1:5050/ | Profile for that demo user |
| Admin | http://127.0.0.1:6060/login | Same demo user. Admin checks the Users table itself (`login-mode=local`) |

Issuer in this demo is `http://127.0.0.1:5050`. Use that host, not `localhost`, so cookies and discovery agree.

Not in this demo: HTTPS, SMTP, Google / Microsoft / WeChat / WhatsApp (those need secrets that are not in the repo), the Swing console, or your existing SQL Server database. Pointing the same jars at a real IS4 database is [GETTING-STARTED.md](GETTING-STARTED.md) and [CONFIG.md](CONFIG.md).

## Docker Compose

Docker and Compose v2. From the repository root:

```bash
docker compose up --build
```

The first build compiles `skoruba4j-sts` and `skoruba4j-admin` inside the image (JDK 21). Later starts reuse the image. Ports are published on `127.0.0.1` only.

Leave that terminal running, then open the URLs in the table above. Admin waits until STS health returns.

Stop with Ctrl+C, then:

```bash
docker compose down
```

To throw away the demo database and signing key:

```bash
docker compose down -v
```

The SQLite file lives in the Compose volume `demo-home`, not in git.

## JDK 21, `java -jar`

No Docker. `JAVA_HOME` / `PATH` must be JDK 21. Git is required the first time, because the SQLite driver is the public snapshot `com.myano:jsqlite:0.1.0-SNAPSHOT` (not on Maven Central). The script installs [JSQLite](https://github.com/Myanowu/jsqlite) revision `90f7ede35801a60cd3ea9e206a03d5cf68b35c14` when that jar is missing. A sibling checkout named `JSQLite` or `jsqlite` is used instead of cloning. The clone, if any, stays under gitignored `demo/runtime/`.

```bash
./demo/run.sh
```

That packages `skoruba4j-sts` (tests skipped; CI still runs them), writes a gitignored home at `demo/runtime/`, and starts the jar. Overlay: [../demo/idserver-demo.yml](../demo/idserver-demo.yml).

Health: http://127.0.0.1:5050/health

Admin, second terminal, same machine:

```bash
./demo/run.sh admin
```

http://127.0.0.1:6060/login

Optional Admin API (same home, HTTP 44302):

```bash
./demo/run.sh admin-api
```

Windows Command Prompt, from the repository root:

```bat
demo\run.cmd
demo\run.cmd admin
```

Stop a process with Ctrl+C. Delete `demo/runtime/` to start from an empty database. Do not commit that directory.

### Windows package (`dist\cmd`)

`mvnw.cmd package` on Windows also copies a JDK into `dist\jdk` and the launchers into `dist\cmd`. Those launchers call `env.cmd`, which sets `IDSERVER_HOME` to the `dist` folder (the parent of `cmd`), then start the jar with the bundled `java.exe`. On Linux or macOS the same scripts are `dist/cmd/start-sts.sh` and `dist/cmd/start-admin.sh`; they use `dist/jdk` when it exists, otherwise `java` on `PATH`. `./demo/run.sh` does not need that tree.

## Sign in and request a token

Browser: open http://127.0.0.1:5050/login and sign in as `demo` / `Passw0rd!`. The home page shows that username. The same pair signs in to Admin at http://127.0.0.1:6060/login, then http://127.0.0.1:6060/admin.

The fresh database seeds a **public** client `skoruba4j-admin` (`RequireClientSecret` is false) with grant types `authorization_code` and `password`. There is no client secret to copy. This curl is the demo password grant, not a production client:

```bash
curl -s -X POST http://127.0.0.1:5050/connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=skoruba4j-admin' \
  -d 'username=demo' \
  -d 'password=Passw0rd!' \
  -d 'scope=openid profile email roles'
```

A successful response is JSON with `access_token` and `token_type` `Bearer`. Authorization code + PKCE is implemented, but this demo does not ship a sample relying party. The seeded redirect URIs point at Admin (`http://127.0.0.1:6060/signin-oidc`). Admin in this demo does not use that redirect; it checks the password locally.

Access-token `aud` is `ApiResources.Name` when the database has API resources. The empty demo seed does not insert one, so do not expect a resource audience until you load a real IS4 configuration database.

## What the pages look like

STS login (`demo` / `Passw0rd!` is printed in this doc because it is the local seed, not a shared secret):

![STS login page](demo/sts-login.png)

Discovery document:

![OpenID discovery JSON](demo/discovery.png)

After sign-in, the STS home page shows the demo user and links discovery plus health:

![Signed-in STS home](demo/sts-home.png)

Admin, after the same demo sign-in, lists the seeded clients. ApiResources stays 0 on this empty file:

![Admin overview](demo/admin-home.png)

## Reset

Docker: `docker compose down -v`, then `docker compose up --build`.

`java -jar`: stop the process and delete `demo/runtime/` (or `$IDSERVER_HOME` if you set it). The next start creates a new file and the DEMO user again, only when `Users` is empty.

Chinese: [zh/demo.md](zh/demo.md).
