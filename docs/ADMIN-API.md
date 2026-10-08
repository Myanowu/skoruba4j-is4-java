# Admin API map

Target compatibility with Skoruba IdentityServer4 Admin API (`api/[controller]`). All routes require a Bearer token and an administration role from the Identity tables.

Do **not** grant that role to every authenticated user.

| Controller | Base path | Typical operations |
|------------|-----------|--------------------|
| Clients | `api/Clients` | list/search, get, create, update, delete; Secrets, Properties, Claims subresources |
| ApiResources | `api/ApiResources` | CRUD + Secrets, Properties |
| ApiScopes | `api/ApiScopes` | CRUD + Properties |
| IdentityResources | `api/IdentityResources` | CRUD + Properties |
| Users | `api/Users` | CRUD, roles, claims, providers, reset password |
| Roles | `api/Roles` | CRUD, users, claims |
| PersistedGrants | `api/PersistedGrants` | list by subject, revoke |

JSON field names should stay close to existing Skoruba DTO names so any already-written callers keep working. Pagination query: `searchText`, `page`, `pageSize`.

Admin API is a JWT resource server. Access tokens must include a `role` claim matching `idserver.admin.role` (from Identity `Roles.Name`). The STS copies those role names onto access tokens. Unauthenticated callers and users without that role receive 401/403. There is no `ForceAdministrationRole`.

JWKS is `${idserver.issuer-uri}/.well-known/openid-configuration/jwks` (IS4 path, not `/oauth2/jwks`).

New user passwords are stored as ASP.NET Identity v3 PBKDF2. Client secrets are stored as IS4 SharedSecret SHA-256 Base64.

Swagger/OpenAPI generator is **not** a required dependency; this markdown is the contract until code exists.

Admin UI calls the same domain services in-process; it must not be the only way to mutate clients.
