# Contributing

This project is a **Skoruba / IdentityServer4 compatibility layer**, not a general IAM.

## Welcome

- Compatibility matrix fixes (`docs/COMPATIBILITY.md`) with tests
- SQL dialect bugs (SQL Server / PostgreSQL / MySQL)
- Password hash edge cases (Identity v2/v3)
- Admin API path parity
- Documentation in English and `docs/zh/`

## Not in scope

- Becoming Keycloak (SAML, LDAP, social IdP marketplace)
- Hibernate / extra frameworks unless required for the protocol
- Copying Skoruba UI assets
- Organization-specific clients, logos, or secrets

## Rules

- Mark a compatibility cell `Y` only with a test or recorded e2e.
- Protocol or `aud` changes must update `docs/PROTOCOL.md` in the same change.
- Keep `oss` samples free of real credentials.
