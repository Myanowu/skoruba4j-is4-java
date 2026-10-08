---
description: skoruba4j-is4-java product constraints for agents in this workspace
---

# Agent constraints

This folder is a **Java drop-in STS / OpenID Provider for IdentityServer4 + Skoruba** (**Skoruba4j**), not a generic IAM.

- Maven `groupId` / Java packages: `com.myano.skoruba4j`. Deployable artifacts: `skoruba4j-sts`, `skoruba4j-admin`, `skoruba4j-admin-api`; optional Swing ops UI `skoruba4j-console`. YAML keys stay `idserver.*` (IS4-oriented config, env `IDSERVER_*`).
- Console DB Manage is **read-only explore + emergency data fix** only—not a replacement for Admin / Admin API.
- Domain CRUD product UI: `skoruba4j-admin`. `skoruba4j-admin-api` keeps Skoruba-compatible JSON `/api/**` (JWT) and may also host a browser console UI (`/`, `/ui/**`, form login).
- Public GitHub tree is `oss` only; the project must stay usable by others (clone, `mvn package`, `/health` + discovery) without private hosts or secrets.
- Local agent notes, when present, live under gitignored `.cursor/notes/` (start with `SESSION-HANDOFF.md`). Do not publish that directory.
- Do not add JPA, Keycloak, Redis, Lombok, or Flyway unless the user explicitly changes the plan.
- Do not commit secrets, organization client ids, or organization logos into the public tree (`oss` profile only). Product mark for **Skoruba4j** lives in `docs/brand/`.
- Map existing IS4 tables; do not introduce `oauth2_registered_client` as the source of truth.
- Keep `/connect/*` paths. Token `aud` is `ApiResources.Name`.
- Verify ASP.NET Identity PBKDF2 hashes; write new hashes in Identity v3 format.
- Do not copy Skoruba `.cshtml`. Do not port `ForceAdministrationRole`.
- Compatibility cells marked Y in `docs/COMPATIBILITY.md` need tests.
- Any private C# reference checkout is read-only and is not part of this repository. Do not copy organization names, client ids, or connection strings from it.
