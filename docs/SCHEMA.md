# Schema

Source of truth for column lists: IdentityServer4.EntityFramework 4.x model + Skoruba Identity table rename.

## Identity tables

| Skoruba (`table-style=skoruba`) | ASP.NET default (`table-style=aspnet`) |
|---------------------------------|----------------------------------------|
| `Users` | `AspNetUsers` |
| `Roles` | `AspNetRoles` |
| `UserRoles` | `AspNetUserRoles` |
| `UserClaims` | `AspNetUserClaims` |
| `RoleClaims` | `AspNetRoleClaims` |
| `UserLogins` | `AspNetUserLogins` |
| `UserTokens` | `AspNetUserTokens` |

`Users` columns (string PK): `Id`, `UserName`, `NormalizedUserName`, `Email`, `NormalizedEmail`, `EmailConfirmed`, `PasswordHash`, `SecurityStamp`, `ConcurrencyStamp`, `PhoneNumber`, `PhoneNumberConfirmed`, `TwoFactorEnabled`, `LockoutEnd`, `LockoutEnabled`, `AccessFailedCount`.

## IS4 configuration (same names in Skoruba)

`ApiResources`, `ApiResourceClaims`, `ApiResourceProperties`, `ApiResourceScopes`, `ApiResourceSecrets`, `ApiScopes`, `ApiScopeClaims`, `ApiScopeProperties`, `Clients`, `ClientClaims`, `ClientCorsOrigins`, `ClientGrantTypes`, `ClientIdPRestrictions`, `ClientPostLogoutRedirectUris`, `ClientProperties`, `ClientRedirectUris`, `ClientScopes`, `ClientSecrets`, `IdentityResources`, `IdentityResourceClaims`, `IdentityResourceProperties`.

`Clients` includes lifetimes, PKCE flags, `RequireClientSecret`, `AllowOfflineAccess`, `AccessTokenType`, etc. (IS4 4.1).

## Operational

`PersistedGrants` (PK `Key`), `DeviceCodes` (PK `UserCode`).

## Admin extras (Skoruba)

`Log`, `AuditLog`.

## Ignore

`DataProtectionKeys` — ASP.NET cookie keys. Java does not read them.

## DDL files

Full CREATE scripts will be `docs/schema/*.sql` (blocked in Plan mode). Draft SQL is in:

- [schema/sqlserver.md](schema/sqlserver.md)
- [schema/postgresql.md](schema/postgresql.md)
- [schema/mysql.md](schema/mysql.md)
- [schema/sqlite.md](schema/sqlite.md)

Production migrations should **reuse the existing database**, not run these scripts. Scripts are for empty **oss demo** databases only. Embedded SQLite (`provider: sqlite`) applies `skoruba4j-domain` classpath DDL on first start (`CREATE TABLE IF NOT EXISTS`), not Flyway.
