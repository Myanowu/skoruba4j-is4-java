# ASP.NET Identity password hashes

Java must verify hashes stored in `Users.PasswordHash` without resetting passwords. New hashes are written in **Identity V3** format so C# STS can still verify during dual-run.

## Formats

Microsoft `PasswordHasher<TUser>`:

- **V2**: PBKDF2-HMAC-SHA1, 1000 iterations, 16-byte salt, 32-byte subkey. Payload version byte `0x00`.
- **V3**: PBKDF2-HMAC-SHA256, default 100_000 iterations (version-dependent), 16-byte salt, 32-byte subkey. Payload version byte `0x01`, plus PRF and iteration fields in the binary layout.

The column is **Base64** of that binary payload (not `$pbkdf2$` MCF).

Implementation: JDK `SecretKeyFactory` / `PBKDF2WithHmacSHA1` and `PBKDF2WithHmacSHA256`. Constant-time compare of subkeys.

## Tests

Generate vectors in unit tests (never commit production hashes):

1. Hash password `Passw0rd!` with a known salt in a fixture, or
2. Create a user in a throwaway .NET `PasswordHasher` and paste **only** that test output into `src/test/resources`.

Document each committed vector as: `password`, `version`, `hash`. Rotate if they ever leak; they are still “known passwords”.

Fixture password used in unit tests: `Passw0rd!` with fixed salt `00112233445566778899aabbccddeeff` (V2 SHA1/1000, V3 SHA256/10000 and 100000).

Implementation class: `com.myano.skoruba4j.domain.password.IdentityPasswordHasher`. New hashes always V3 SHA256 / 100_000 iterations. V2 or older V3 iteration counts verify as `SUCCESS_REHASH_NEEDED`.

## Lookup

Login resolution (Skoruba-style): try `NormalizedUserName` then `NormalizedEmail` depending on `idserver.login.resolution-policy`.
