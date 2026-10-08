# SQL Server demo DDL (draft)

**Do not run against a production IS4 database.** For empty oss demo only. Convert to `sqlserver.sql` in Agent mode.

Identity uses Skoruba names. Types follow EF Core SQL Server snapshot (nvarchar, bit, datetimeoffset).

```sql
-- Identity (skoruba table-style)
CREATE TABLE Users (
  Id nvarchar(450) NOT NULL PRIMARY KEY,
  UserName nvarchar(256) NULL,
  NormalizedUserName nvarchar(256) NULL,
  Email nvarchar(256) NULL,
  NormalizedEmail nvarchar(256) NULL,
  EmailConfirmed bit NOT NULL,
  PasswordHash nvarchar(max) NULL,
  SecurityStamp nvarchar(max) NULL,
  ConcurrencyStamp nvarchar(max) NULL,
  PhoneNumber nvarchar(max) NULL,
  PhoneNumberConfirmed bit NOT NULL,
  TwoFactorEnabled bit NOT NULL,
  LockoutEnd datetimeoffset NULL,
  LockoutEnabled bit NOT NULL,
  AccessFailedCount int NOT NULL
);
CREATE UNIQUE INDEX UserNameIndex ON Users(NormalizedUserName) WHERE NormalizedUserName IS NOT NULL;
CREATE INDEX EmailIndex ON Users(NormalizedEmail);

CREATE TABLE Roles (
  Id nvarchar(450) NOT NULL PRIMARY KEY,
  Name nvarchar(256) NULL,
  NormalizedName nvarchar(256) NULL,
  ConcurrencyStamp nvarchar(max) NULL
);

CREATE TABLE UserRoles (
  UserId nvarchar(450) NOT NULL,
  RoleId nvarchar(450) NOT NULL,
  PRIMARY KEY (UserId, RoleId)
);

CREATE TABLE UserClaims (
  Id int IDENTITY NOT NULL PRIMARY KEY,
  UserId nvarchar(450) NOT NULL,
  ClaimType nvarchar(max) NULL,
  ClaimValue nvarchar(max) NULL
);

CREATE TABLE RoleClaims (
  Id int IDENTITY NOT NULL PRIMARY KEY,
  RoleId nvarchar(450) NOT NULL,
  ClaimType nvarchar(max) NULL,
  ClaimValue nvarchar(max) NULL
);

CREATE TABLE UserLogins (
  LoginProvider nvarchar(450) NOT NULL,
  ProviderKey nvarchar(450) NOT NULL,
  ProviderDisplayName nvarchar(max) NULL,
  UserId nvarchar(450) NOT NULL,
  PRIMARY KEY (LoginProvider, ProviderKey)
);

CREATE TABLE UserTokens (
  UserId nvarchar(450) NOT NULL,
  LoginProvider nvarchar(450) NOT NULL,
  Name nvarchar(450) NOT NULL,
  Value nvarchar(max) NULL,
  PRIMARY KEY (UserId, LoginProvider, Name)
);

CREATE TABLE PersistedGrants (
  [Key] nvarchar(200) NOT NULL PRIMARY KEY,
  ClientId nvarchar(200) NOT NULL,
  Type nvarchar(50) NOT NULL,
  SubjectId nvarchar(200) NULL,
  SessionId nvarchar(100) NULL,
  Description nvarchar(200) NULL,
  CreationTime datetime2 NOT NULL,
  Expiration datetime2 NULL,
  ConsumedTime datetime2 NULL,
  Data nvarchar(max) NOT NULL
);

CREATE TABLE DeviceCodes (
  UserCode nvarchar(200) NOT NULL PRIMARY KEY,
  DeviceCode nvarchar(200) NOT NULL UNIQUE,
  ClientId nvarchar(200) NOT NULL,
  SubjectId nvarchar(200) NULL,
  SessionId nvarchar(100) NULL,
  Description nvarchar(200) NULL,
  CreationTime datetime2 NOT NULL,
  Expiration datetime2 NOT NULL,
  Data nvarchar(max) NOT NULL
);
```

`Clients` / `ApiResources` / related tables: create from IdentityServer4.EntityFramework.Storage 4.1 schema (many columns). Prefer copying an empty database from a Skoruba template rather than hand-maintaining every Client flag in v1. A complete Clients script is a follow-up when Maven starts.

`AuditLog` / `Log`: optional for demo.
