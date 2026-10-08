# 使用手册

这是下载项目之后的最短路径。不用单独安装数据库。第一次启动会在你自己的机器上新建一个空的 SQLite 文件，并写入 DEMO 用户。这个文件只留在本机，不会进 Git。

产品名是 **Skoruba4j**。建议的 GitHub 仓库名是 **skoruba4j-is4-java**。

## 你会看到的名字

| 出现的地方 | 名字 | 含义 |
|------------|------|------|
| 产品、窗口标题、文档 | Skoruba4j | 面向已有 IdentityServer4 + Skoruba 数据库的 Java STS |
| Maven 模块 | `skoruba4j-sts`、`skoruba4j-admin`、`skoruba4j-admin-api`、`skoruba4j-console` | 三个进程，外加可选的 Swing 控制台 |
| Java 包 | `com.myano.skoruba4j` | 构建坐标 |
| YAML 与环境变量 | `idserver.*`、`IDSERVER_*` | 配置前缀，不是第二个产品名 |
| SQLite 文件 | `data/skoruba4j.sqlite` | 运行时出现在 `IDSERVER_HOME` 下面 |

`skoruba4j-domain` 和 `skoruba4j-protocol` 是库，不用单独启动。

## 需要准备

- JDK **21**。`JAVA_HOME` 必须指向这个 JDK，不要用 PATH 上更旧的 Java。
- Git。第一次构建时 Maven 需要能下载依赖。

## 1. 指定同一个目录

三个进程必须使用**同一个** `IDSERVER_HOME`。不设置的话，每个 Maven 模块会用自己的工作目录，各建一份数据库。

在仓库根目录：

```bash
export JAVA_HOME=/path/to/jdk-21
export IDSERVER_HOME="$PWD"
```

Windows 命令提示符：

```bat
set JAVA_HOME=C:\path\to\jdk-21
set IDSERVER_HOME=%CD%
```

Windows PowerShell：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:IDSERVER_HOME = (Get-Location).Path
```

之后每个用来启动进程的终端都要带上这两个变量。

## 2. 启动 STS

```bash
./mvnw -pl skoruba4j-sts -am spring-boot:run
```

Windows 用 `mvnw.cmd -pl skoruba4j-sts -am spring-boot:run`。

第一次启动会写出：

`%IDSERVER_HOME%/data/skoruba4j.sqlite`

因为 `Users` 是空的，会同时写入下面的 DEMO 账号。这个终端保持运行。

| 检查 | 地址 |
|------|------|
| 健康 | http://127.0.0.1:5050/health |
| Discovery | http://127.0.0.1:5050/.well-known/openid-configuration |
| 登录 | http://127.0.0.1:5050/login |

`/health` 里应有 `"database":"UP"`。

用 `demo` / `Passw0rd!` 登录。这个密码只给本机这个文件用。

## 3. 启动 Admin

新开一个终端，`JAVA_HOME` 和 `IDSERVER_HOME` 与上面相同：

```bash
./mvnw -pl skoruba4j-admin -am spring-boot:run
```

打开 http://127.0.0.1:6060/login ，用同一个 DEMO 用户。登录后的页面是 http://127.0.0.1:6060/admin 。

公开默认是 `idserver.admin.login-mode=local`：Admin 直接查 Users 表，第一次试用不必走 STS 浏览器跳转。按上面的顺序先启动 STS，数据库文件由 STS 创建，Admin 接着用同一份。

## 4. Admin API（可选）

新终端，变量相同：

```bash
./mvnw -pl skoruba4j-admin-api -am spring-boot:run
```

健康检查：http://127.0.0.1:44302/health

## 停掉并重新开始

每个进程用 Ctrl+C 停掉。如果这些文件存在，删掉它们：

- `$IDSERVER_HOME/data/skoruba4j.sqlite`
- `$IDSERVER_HOME/data/skoruba4j.sqlite-wal`
- `$IDSERVER_HOME/data/skoruba4j.sqlite-shm`

再启动 STS。会新建空库和新的 DEMO 用户。

不要提交这些文件。`.gitignore` 已经排除 `data/`、`*.sqlite`、`*.sqlite-wal`、`*.sqlite-shm`。`git status` 里不应出现它们。不要 `git add -f`。

## 接到已有的 IS4 数据库

SQLite 文件同样不要进 Git。要用 SQL Server、PostgreSQL 或 MySQL，把 [../../config/idserver-local.yml.example](../../config/idserver-local.yml.example) 复制为 `config/idserver-local.yml`（已被忽略），再改 `idserver.db.provider` 和 `idserver.db.url`。键的说明在英文 [../CONFIG.md](../CONFIG.md)。生产密码不要写进会被提交的文件。

`Users` 里已经有行时，不会再写入 DEMO 用户。请用已有、且带管理角色的账号（默认角色名 `MyRole`，除非改过 `idserver.admin.role`）。

## 接着读

| 文档 | 用途 |
|------|------|
| [FIRST-LOGIN.md](FIRST-LOGIN.md) | DEMO 用户和 Admin 登录方式 |
| [../CONFIG.md](../CONFIG.md) | 端口、profile、数据库键（英文） |
| [ABOUT.md](ABOUT.md) | 这个产品是什么 |
| [../COMPATIBILITY.md](../COMPATIBILITY.md) | 协议做到哪一步（英文） |
| [MIGRATION.md](MIGRATION.md) | 从正在运行的 IS4 库切换 |

英文步骤：[../GETTING-STARTED.md](../GETTING-STARTED.md)。
