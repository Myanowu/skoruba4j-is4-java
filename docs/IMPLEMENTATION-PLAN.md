# Implementation plan

Execute in this repository. Do not modify any private C# reference checkout; it is not part of this tree.

## Modules

```
skoruba4j-domain      # JDBC + PBKDF2, no Spring
skoruba4j-protocol    # SAS settings, /connect, aud customizer, delegation
skoruba4j-sts
skoruba4j-admin
skoruba4j-admin-api
skoruba4j-console   # optional Swing ops UI; the three processes stay independently publishable
```

## Order

1. Maven parent + three Boot apps empty `/health` (ports in CONFIG.md).
2. Domain repositories against existing table names; `table-style` switch.
3. PasswordEncoder + unit tests (PASSWORD.md).
4. STS: discovery, authorize, token, PKCE, `aud`.
5. STS extras: refresh, logout, userinfo, introspect, delegation, login HTML.
6. Admin API controllers (ADMIN-API.md).
7. Admin UI HTML + OIDC client.
8. Optional Swing `skoruba4j-console` (one desktop console; three processes still independently publishable).
9. PostgreSQL/MySQL/SQLite dialects (SQLite = bundled first-install file).
10. Private e2e against real clients (not committed).
11. GitHub checklist (PUBLISHING.md).

## Allow-listed Maven coordinates

See CONFIG.md. No JPA, Lombok, MapStruct, Thymeleaf, springdoc, Flyway, Redis, Keycloak.

## Demo vs private

- `oss`: `samples/` demo clients.
- `local`: gitignored yaml (`application-local.yml`); never push.
