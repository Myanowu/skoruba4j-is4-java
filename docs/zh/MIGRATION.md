# 迁移手册（中文摘要）

完整步骤以英文 [../MIGRATION.md](../MIGRATION.md) 为准。

1. 只读导出 Clients / ApiResources / 用户规模 / 是否使用 delegation。  
2. 确认表名是 `Users` 还是 `AspNetUsers`。  
3. Java STS 连**同一库**、先用旁路主机名。  
4. 核对 discovery、授权码、JWT 的 `iss`/`aud`/`sub`/`scope`。  
5. 原密码抽样登录。  
6. 切 DNS；cookie 全部失效。  
7. 回滚：DNS 改回 C#；哈希格式仍兼容。
