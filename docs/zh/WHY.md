# 为何不是 Keycloak

任务是：在 **不改业务 OIDC 客户端、不重置 ASP.NET 密码、不重建 IS4 表** 的前提下，把 STS 换成 Java。

- 继续用 .NET → Duende  
- 能改 Authority / 用户 / 客户端 → Keycloak 或 MaxKey  
- 已有 IS4 SQL 且客户端锁死 `/connect` 与 `ApiName=aud` → 本项目  

完整英文论述见 [../WHY.md](../WHY.md)。
