# Compatibility matrix

Legend: **Y** = will ship with tests; **P** = planned / partial; **N** = out of scope; **?** = not decided until inventory.

Update this file in the same change as code. Do not mark **Y** without a test.

## Endpoints (IS4 names)

| Path | C# IS4 | This project | Notes |
|------|--------|--------------|--------|
| `/.well-known/openid-configuration` | Y | Y | |
| JWKS (`jwks_uri`) | Y | Y | Public; JWT `kid` matches JWKS |
| `/connect/authorize` | Y | Y | + PKCE |
| `/connect/token` | Y | Y | |
| `/connect/userinfo` | Y | Y | |
| `/connect/introspect` | Y | P | |
| `/connect/revocation` | Y | P | |
| `/connect/endsession` | Y | Y | `idserver.logout.end-session=compatible` (default) or `strict` (expired token → re-login, no refresh) |
| `/connect/deviceauthorization` | Y | P | Phase 2 |
| `/connect/mtls/*` | P | N | |

## Grants

| Grant | Default |
|-------|---------|
| authorization_code + PKCE | Y |
| refresh_token / `offline_access` | Y |
| client_credentials | P |
| `delegation` (IS4 extension) | P | converter + provider path; advertise only when `Clients` has the grant |
| device_code | P |
| implicit | N |
| resource owner password | P | client must allow `password` grant; Admin `sts-password` login uses this |

## Token behaviour

| Behaviour | Status |
|-----------|--------|
| JWT access tokens | Y |
| `aud` = `ApiResources.Name` via `ApiResourceScopes` | Y |
| `sub` = Identity user id | Y |
| `scope` claim present | Y |
| Opaque / reference tokens | N (unless later) |
| ASP.NET Data Protection cookies | N |

## Identity / login

| Feature | Status |
|---------|--------|
| Username or email login | Y |
| Self-drawn `/login` HTML | Y |
| Lockout | Y |
| ASP.NET Identity v2/v3 password verify | Y |
| Write new hashes as v3 | Y |
| Forgot password (SMTP) | Y | `/forgot-password`; empty `idserver.smtp.host` skips send; does not reveal whether the account exists |
| Register | P |
| reCAPTCHA (optional) | P |
| TOTP (`TwoFactorEnabled`) | P |
| GitHub / Azure AD external | N unless a deployment already enabled them |

## Admin

| Feature | Status |
|---------|--------|
| Admin UI (original HTML, OIDC to STS) | Y | self-drawn HTML; `idserver.admin.login-mode`: `local` \| `sts-password` \| `sts-oidc` (`oidc-enabled` legacy) |
| Admin API `api/Clients` … `api/Users` | Y (shape as close as practical) |
| Force admin role on every user | N (rejected) |
| Skoruba pixel-identical Razor | N |

## Data stores

| Store | Status |
|-------|--------|
| SQL Server | Y |
| PostgreSQL | Y |
| MySQL | Y |
| IS4 configuration tables | Y |
| Identity tables `skoruba` or `aspnet` names | Y |
| `PersistedGrants` / `DeviceCodes` | P | Admin list/revoke; STS writes `PersistedGrants` via `PersistedGrantOAuth2AuthorizationService` (auth code / refresh index + JSON container). DeviceCodes still TBD |
| Stable JWT signing JWK file | Y | `idserver.signing.jwk-file` (IS4 `tempkey.jwk` analogue) |
| Encrypted STS auth cookie (survive restart) | Y | `SKORUBA4J_STS_AUTH` + `idserver.auth-cookie.key-file` (not ASP.NET DP) |
| `AuditLog` browse / delete-older | Y | Admin `/admin/audit-logs`; table `AuditLog` |
| `AuditLog` / `Log` write | P | Admin mutating POSTs write lightweight `AdminRequestEvent`; full Skoruba event taxonomy not ported; `Log` (errors) not yet |
| `DataProtectionKeys` | N | Do not decrypt C# cookies; Java uses its own key file |
