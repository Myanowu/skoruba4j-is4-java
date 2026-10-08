# Demo data (oss)

The public demo does not load JSON seed files. On the first SQLite start, when the `Users` table is empty, the process creates tables and inserts a DEMO identity store.

| Field | Value |
|-------|--------|
| Username | `demo` |
| Password | `Passw0rd!` |
| Role | `MyRole` |
| Admin client | `skoruba4j-admin` |

`Passw0rd!` is a local DEMO password. Do not use it in production, and do not commit a real client secret here.

Sign-in details: [docs/FIRST-LOGIN.md](../docs/FIRST-LOGIN.md). DDL location: [docs/schema/sqlite.md](../docs/schema/sqlite.md).
