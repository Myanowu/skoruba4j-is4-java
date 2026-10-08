# Cutover and rollback

## Dual-run

C# STS and Java STS may share the **user and client** tables. They must **not** share ASP.NET Data Protection keys. Browser sessions are not portable.

Signing keys should be **different** until you cut DNS, or resource servers will accept tokens from both issuers if `iss` is identical — only do identical `iss` when Java fully replaces C# on that hostname.

Recommended: Java on a side hostname until protocol tests pass, then:

1. Reverse proxy: old public URL → Java
2. Or change `Authority` in every API (worse)

## What breaks at T0

- All existing cookies (users sign in again)
- Refresh tokens issued by C#, unless you implement IS4 PersistedGrant read compatibility (phase 2; treat as **P**)
- In-flight authorization codes

## Rollback

1. Point proxy/DNS back to C# STS.
2. Keep Java off the public issuer.
3. Passwords still work on C# because hashes stay Identity v3.

## Signing key compromise

Rotate Java keys independently. Publish JWKS. Old access tokens expire by `exp`.
