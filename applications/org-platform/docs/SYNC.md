# Sync with Skoruba4j Admin API

org-platform never depends on skoruba4j JARs. Configure HTTP access:

```yaml
orgplatform:
  idp:
    admin-api-base-url: http://localhost:8001
    sync-enabled: false   # set true when Admin API + client credentials are ready
    # bearer token for machine client, or leave empty to skip
    bearer-token: ${ORGPLATFORM_IDP_BEARER_TOKEN:}
```

## Claims/Sync

```http
PUT {admin-api-base-url}/api/Users/{userId}/Claims/Sync
Authorization: Bearer …
Content-Type: application/json

{
  "claims": [
    { "type": "org_id", "value": "1" },
    { "type": "org_path", "value": "/HQ/Finance" },
    { "type": "department", "value": "Finance" },
    { "type": "org_role", "value": "Manager" }
  ]
}
```

Triggered when membership is saved (primary org assignment). Claim types must be registered on the IdP Identity/API resources — see `.cursor/notes/CLAIM-TYPES.md` in the skoruba4j tree.

## OIDC login (org console)

`orgplatform.security.mode=oidc` (the default) sends the browser to STS. The ID token must contain a `role` claim equal to `orgplatform.security.admin-role` (default `MyRole`). A signed-in user without that role is refused. This app does not grant the role.

Register an authorization-code client in Skoruba Admin (public client, PKCE, no secret unless `ORGPLATFORM_OIDC_CLIENT_SECRET` is set):

| Setting | Value |
|---------|--------|
| Client ID | `org-platform` (or `ORGPLATFORM_OIDC_CLIENT_ID`) |
| Grant | `authorization_code` |
| PKCE | required |
| Scopes | `openid`, `profile` |
| Redirect URI | `http://localhost:8088/login/oauth2/code/sts` |
| Post logout | `http://localhost:8088/` |

Issuer default is `http://127.0.0.1:5050` (`ORGPLATFORM_ISSUER_URI`). `/health` stays open.
