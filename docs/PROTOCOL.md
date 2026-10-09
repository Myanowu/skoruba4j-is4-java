# Protocol notes (IS4-compatible)

Product identity and the public protocol list (Y / P / N) live in [ABOUT.md](ABOUT.md). This page is the implementation map: Spring Authorization Server is remapped onto IdentityServer4 paths.

## Endpoint mapping

Spring Authorization Server defaults to `/oauth2/authorize` and `/oauth2/token`. This project **must** remap:

| IS4 (keep) | SAS default (do not expose as primary) |
|------------|----------------------------------------|
| `/connect/authorize` | `/oauth2/authorize` |
| `/connect/token` | `/oauth2/token` |
| `/connect/userinfo` | `/userinfo` |
| `/connect/introspect` | `/oauth2/introspect` |
| `/connect/revocation` | `/oauth2/revoke` |
| `/connect/endsession` | end-session |
| `/connect/deviceauthorization` | `/oauth2/device_authorization` |

Discovery (`/.well-known/openid-configuration`) must advertise the **IS4 paths**.

## Audience (`aud`) is not the scope name

IdentityServer4 builds access-token audiences from **API resources**, not from the raw scope string.

1. The client requests `scope` (identity scopes + API scopes).
2. Each API scope is joined through `ApiResourceScopes` to an `ApiResources` row.
3. The JWT `aud` claim is `ApiResources.Name`.
4. A resource server using `AddIdentityServerAuthentication` / `ApiName` succeeds only if `ApiName` equals that `aud`.

If two scopes map to one API resource, `aud` may still be a single string. If they map to two resources, `aud` may be an array. APIs that compare `aud` as a single string can break; document this when implementing the token customizer.

Do not put the API **scope** name in `aud` unless that string is also the resource name.

## Subject

`sub` is the ASP.NET Identity user primary key (`Users.Id` / `AspNetUsers.Id`), typically a GUID string.

## UserClaims / RoleClaims on tokens

IdentityServer4’s profile service only emits claim types requested by the granted scopes’ resources. This project does the same:

1. Load `UserClaims` plus `RoleClaims` for the user’s roles.
2. Keep types listed on `IdentityResourceClaims` / `ApiScopeClaims` / `ApiResourceClaims` for those scopes.
3. **id_token**: include filtered identity-resource claims only when `Clients.AlwaysIncludeUserClaimsInIdToken` is true; otherwise they appear on **userinfo**.
4. **access_token**: include filtered ApiScope / ApiResource claim types.
5. Do not overwrite reserved JWT / protocol names (`sub`, `role`, `scope`, …).

## Client secrets

IS4 `ClientSecrets.Value` is often a **SHA-256 hash** of the secret (`Type` = SharedSecret). Token-endpoint authentication must hash the incoming secret the same way. Never treat the column as UTF-8 plaintext without checking `Type`.

## `delegation` grant

Existing C# validator:

- `grant_type=delegation`
- form field `token` = an access token
- validate token; take `sub`; issue a new grant for that subject

Reimplement as an SAS extension grant. If no client uses it, keep the code path but do not advertise it in demo clients.

## Issuer

`iss` must match what resource servers already have as Authority (or you must change every API). Prefer keeping the public hostname via reverse proxy even if the process is Java.
