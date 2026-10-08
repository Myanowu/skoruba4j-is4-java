# Security

This software is an identity provider. Treat issues as high severity.

## Disclosure

Please do **not** open a public GitHub issue for credential theft, token forgery, or auth bypass. Use a private channel (repository security advisory once the GitHub repo exists).

## Honest limits

- Not OpenID Certified.
- Protocol engine is Spring Authorization Server; compatibility shims are this project’s risk surface.
- Existing ASP.NET Data Protection cookies are **not** accepted. Sessions reset on cutover.
- Demo passwords in `samples/` are for local only.

## Threat notes for operators

- Pin signing keys; do not use ephemeral keys in production.
- Client secrets in IS4 tables are often SHA-256 hashes; never log them.
- Do not enable “every authenticated user is an administrator” (a pattern that existed in some Skoruba forks).
