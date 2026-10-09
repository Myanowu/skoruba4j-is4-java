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
| `/connect/introspect` | Y | Y | `StsIntrospectRevocationTest` |
| `/connect/revocation` | Y | Y | `StsIntrospectRevocationTest` |
| `/connect/endsession` | Y | Y | `idserver.logout.end-session=compatible` (default) or `strict` (expired token → re-login, no refresh) |
| `/connect/deviceauthorization` | Y | P | Phase 2 |
| `/connect/mtls/*` | P | N | |

## Grants

| Grant | Default |
|-------|---------|
| authorization_code + PKCE | Y |
| refresh_token / `offline_access` | Y |
| client_credentials | Y | `StsIntrospectRevocationTest` (token + introspect) |
| `delegation` (IS4 extension) | P | converter + provider path; advertise only when `Clients` has the grant |
| device_code | P |
| implicit | N |
| resource owner password | Y | client must allow `password`; Admin `sts-password` uses it; `StsPasswordGrantTest` |

## Token behaviour

| Behaviour | Status |
|-----------|--------|
| JWT access tokens | Y |
| `aud` = `ApiResources.Name` via `ApiResourceScopes` | Y |
| `sub` = Identity user id | Y |
| `scope` claim present | Y |
| UserClaims / RoleClaims filtered by resource claim types | Y | `Is4UserProfileClaimsTest`; id_token only if `AlwaysIncludeUserClaimsInIdToken` |
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
| Register | P | Off by default (`idserver.login.allow-register`); math captcha optional |
| reCAPTCHA (optional) | N | Use built-in math captcha when register is on; no Google reCAPTCHA |
| Account self-service (signed-in `/`) | Y | Profile, unlink external logins, change password, grants; `/account` redirects home |
| Login branding (`idserver.brand.*`) | Y | Product name + tagline on login/register |
| Bulk users `POST /api/Users/Bulk` | P | Cap 200; not SCIM; endpoint shipped |
| Org claim sync `PUT /api/Users/{id}/Claims/Sync` | Y | `UserClaimsReplaceTest`; replaces listed types only |
| TOTP (`TwoFactorEnabled`) | Y | `TotpCodesTest`; STS `/login/2fa` when flag + AuthenticatorKey; Admin generate key |
| Microsoft Entra ID (Azure AD) external | P | STS `idserver.microsoft.*` + ClientProperties; link-existing |
| Google external | P | STS `idserver.google.*` + ClientProperties; link-existing |
| WeChat QR external | P | STS `/external/wechat`; ClientProperties `skoruba4j.external.wechat` |
| WhatsApp QR reply-login | P | STS `/external/whatsapp` + Meta webhook |
| GitHub external | N | Not implemented |

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
| `AuditLog` / `Log` write | P | Admin mutating POSTs + STS login success/failure + token issued/failure via `AuditLogWriter`; full Skoruba event taxonomy / `Log` (errors) not yet |
| `DataProtectionKeys` | N | Do not decrypt C# cookies; Java uses its own key file |
