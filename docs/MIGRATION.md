# Migration playbook (Skoruba / IS4 → this Java STS)

**Status:** procedure is complete; run it only after STS MVP exists.

## 0. Preconditions

- JDK 21 on the jump host
- Read-only access to the current Identity / Configuration / PersistedGrant database
- One non-production OIDC client you can break
- `idserver.identity.table-style` chosen: `skoruba` if you have `Users`; `aspnet` if you have `AspNetUsers`

## 1. Inventory (read-only SQL)

Export or screenshot:

- `Clients` (`ClientId`, grant types, PKCE, redirects)
- `ClientScopes`
- `ApiResources` + `ApiResourceScopes` (this drives `aud`)
- `ApiScopes`, `IdentityResources`
- User count and whether `PasswordHash` looks like Identity v2/v3
- Whether anyone uses `grant_type=delegation`, device flow, `offline_access`

## 2. Compatibility gap

Fill [COMPATIBILITY.md](COMPATIBILITY.md) for **your** grants. If you still use implicit or resource-owner password, treat them as out of default scope until explicitly enabled.

## 3. Side-by-side STS

1. Keep the C# STS running.
2. Point Java STS at the **same** database (`ddl` does not create tables).
3. Bind Java to a **new** port or host (example `https://id-java.example:5051`).
4. Do not cut DNS yet.

## 4. Protocol probe

For one client:

1. Hit `/.well-known/openid-configuration` on Java; confirm `authorization_endpoint` still ends with `/connect/authorize`.
2. Authorization code + PKCE login with an existing user password.
3. Decode the access token. Compare with C# tokens:
   - `sub` = user `Id` (string)
   - `aud` = `ApiResources.Name` for the requested API scopes
   - `scope` contains the strings the client sent
   - `iss` equals the public Authority you will keep or the new host you will set in APIs
4. Call a resource API that uses IdentityServer authentication / `ApiName`. `IsAuthenticated` must be true.

## 5. Password sample

Log in as several existing users (not only seed admin). Failures almost always mean hash format or `NormalizedUserName` lookup.

## 6. Admin

Point Admin UI / Admin API at the Java STS issuer. Roles come from the `Roles` / `UserRoles` tables. Do **not** inject an admin role for every user.

## 7. Cut over

Follow [CUTOVER.md](CUTOVER.md): DNS or reverse proxy to Java; C# process off; expect all cookies to drop.

## 8. Rollback

Point DNS back to C# STS. Password hashes remain Identity-format, so C# can verify them. Outstanding Java-issued JWTs fail once you stop trusting Java’s signing key.

## 9. Do not copy into public git

Client lists, connection strings, and real redirect URIs of production apps stay in private config.
