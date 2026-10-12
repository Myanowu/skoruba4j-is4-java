# org-platform

Independent **organization + workflow + forms + self-query** app under `applications/`.  
Does **not** modify `skoruba4j-sts` / `skoruba4j-admin` / `skoruba4j-admin-api`, and is **not** in the root Maven reactor.

## Build

```bash
mvn -f applications/org-platform/pom.xml package
```

Root `mvn package` from the repo root is unchanged (skoruba4j modules only).

## Run API

```bash
mvn -f applications/org-platform/org-api/pom.xml spring-boot:run
```

- Health: http://localhost:8088/health  
- Admin UI (static): http://localhost:8088/  
- Config prefix: `orgplatform.*` (env `ORGPLATFORM_*`)

The console requires an STS login. The signed-in user must have the Identity role named by `orgplatform.security.admin-role` (default `MyRole`, the same value as `idserver.admin.role`).  
Set `orgplatform.security.mode=open` only for a local demo with no IdP. Tests do that. See [docs/SYNC.md](docs/SYNC.md) for the OIDC client.

## Modules

| Module | Role |
|--------|------|
| `org-api` | JDBC API, org tree, forms, linear workflow, inbox, self-query |
| `org-web` | Source of the admin/portal static UI (copied into org-api resources on package) |

## Integration with Skoruba4j

HTTP only — see [docs/SYNC.md](docs/SYNC.md). No Maven dependency on `skoruba4j-*`.
