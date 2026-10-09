# Horizontal scale (no Redis)

Skoruba4j stays JDBC-only. Multiple STS / Admin instances are possible if operators accept the limits below.

## What is shared (safe across nodes)

| Asset | Storage | Notes |
|-------|---------|-------|
| Clients, Users, Roles, resources | IS4 / Identity DB | All nodes use the same JDBC URL |
| Refresh / auth codes | `PersistedGrants` | STS writes via `PersistedGrantOAuth2AuthorizationService` |
| Signing keys | `idserver.signing.jwk-file` | **Same file (or copy) on every STS** or tokens from node A fail JWKS on node B |
| Auth cookie key | `idserver.auth-cookie.key-file` | Same key file on every STS if sticky sessions are off |

## What is process-local

| Asset | Impact |
|-------|--------|
| Servlet HTTP session (form login, `/login/2fa`, register captcha, WhatsApp QR pending) | Needs **sticky sessions** at the load balancer, or users hit the same node until login finishes |
| In-memory caches (if any) | Treat as optional; do not rely on them for authz |

## Recommended patterns

1. **Single STS** behind a reverse proxy (simplest; matches most IS4 cutovers).
2. **N STS + sticky cookie** on `/login` and `/connect/authorize` if you need capacity.
3. Put HTTPS termination on the proxy; keep `idserver.issuer-uri` equal to the public Authority every RP already uses.
4. Do **not** introduce Redis for sessions unless the product plan changes (`AGENTS.md`).

## Admin / Admin API

Admin UI session cookies are per process (`SKORUBA4J_ADMIN_SESSION`). Prefer one Admin instance, or sticky sessions. Admin API is JWT to the shared STS issuer — horizontally fine once JWKS/`iss` match.
