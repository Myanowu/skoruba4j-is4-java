# Why not Keycloak / Duende / MaxKey

## The job

Move an existing **IdentityServer4 + Skoruba Admin** deployment onto a **JVM process** while keeping:

1. The same SQL tables (or a one-time read of those tables)
2. The same OIDC clients (`/connect/*`, PKCE, redirect URIs)
3. The same user passwords (ASP.NET Identity hashes)
4. The same access-token `aud` rule: **ApiResource.Name**, which `IdentityServer4.AccessTokenValidation` and similar middleware expect

## Alternatives

| Option | When it is better | Why we still built this |
|--------|-------------------|-------------------------|
| **Duende IdentityServer** | You stay on .NET | Commercial license; not Java |
| **Keycloak** | You can change Authority URLs, clients, and users | New realm model, new paths, new hashes, new `aud` |
| **Casdoor / MaxKey** | Greenfield IAM, social login, LDAP | Different schema and protocol surface |
| **Spring Authorization Server alone** | New clients you control | Default paths `/oauth2/*`, default JDBC tables, no Skoruba Admin API |
| **SAS demo repos** | Workshops | No IS4 table adapter, no PBKDF2 Identity hashes |

## One-sentence pitch

> Drop-in Java STS for people who already paid the cost of IS4 configuration data and cannot reopen every application.

If that sentence is false for a team, they should not use this project.
