# 在线考试系统（OES）

Java 17 + Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis + S3 兼容对象存储；前端 Vue 3（待建）。

```
docs/      需求分析与方案设计、环境检查报告、开发日志
backend/   Spring Boot 后端（单模块，按 controller/service/mapper 分包）
deploy/    docker-compose 与初始化 SQL、环境自检脚本
```

---

## 一、本地运行

### 0. 前置条件

| 项 | 要求 | 本机状态 |
|---|---|---|
| JDK | 17 | `D:\develop\jdk\current` 已指向 `jdk-17.0.2` |
| Docker Desktop | 运行中 | 需手动启动（引擎依赖 wsl.exe） |
| 中间件 | MySQL/Redis/对象存储 | 见下方命令 |

```bash
cd deploy
docker compose up -d          # 首次会自动执行 sql/01-bootstrap.sql
docker compose ps             # 三个容器应为 running / healthy
```

连接参数（`application.yml` 已设默认值，无需改动）：

```
MySQL  localhost:3306  oes / oes123456
Redis  localhost:6379  密码 redis123456
S3     localhost:9000  minioadmin / minio123456  bucket=oes-file
```

### 1. VS Code（推荐，本机已装 Java + Spring Boot 插件）

1. **打开 `backend` 目录**，不是项目根目录。Java 语言服务器靠 `pom.xml` 定位工程，选根目录会导致无法识别为 Maven 项目。
2. 等右下角 Java 语言服务器加载完成（首次 30 秒 ~2 分钟，会下载/索引依赖）。
3. 两种启动方式：
   - 打开 `src/main/java/com/example/oes/OesApplication.java`，点 `main` 方法上方的 **Run**；
   - 或左侧 **Spring Boot Dashboard** → APPS → `oes-backend` → 启动。
4. 控制台出现 `Started OesApplication in x seconds` 即成功，端口 8080。

> 若提示 JDK 版本不符：`Ctrl+,` 搜索 `java.configuration.runtimes`，确认 17 的路径是 `D:\develop\jdk\jdk-17.0.2`。

### 2. 命令行

```bash
cd backend
mvn spring-boot:run
# 或
mvn -DskipTests package && java -jar target/oes-backend-1.0.0-SNAPSHOT.jar
```

### 3. IntelliJ IDEA（本机未安装，如后续安装）

- `File → Open` 选 `backend/pom.xml`，以 Project 方式打开；
- `Project Structure → SDK` 选 17（路径指到具体版本目录 `jdk-17.0.2`，不要指 `current` 链接）；
- `Settings → Build Tools → Maven`：Maven home 用 `D:\develop\maven\apache-maven-3.9.16`，User settings file 用 `C:\Users\Admin\.m2\settings.xml`——与命令行保持一致，否则本地仓库会分裂成两份重复下载。

---

## 二、验证清单

启动后按顺序执行，全部通过说明环境与代码都正常。

| # | 操作 | 期望 |
|---|---|---|
| 1 | 打开 http://localhost:8080/doc.html | Knife4j 文档页，显示 5 个接口 |
| 2 | `GET /api/ping` | `200`，`{"code":0,...,"java":"17.0.2"}` |
| 3 | `POST /api/auth/login` | `200`，返回 token 与角色 |
| 4 | `GET /api/auth/me`（带 token） | `200`，返回当前用户 |
| 5 | `GET /api/admin/ping`（学生 token） | **403** |
| 6 | `POST /api/auth/logout` 后复用 token | **401 / 40102** |

演示账号：`admin` / `teacher` / `student`，密码均为 `123456`。

命令行验证（Windows 注意用 `curl.exe`，PowerShell 里 `curl` 是 `Invoke-WebRequest` 的别名）：

```bash
curl.exe -s http://localhost:8080/api/ping

curl.exe -s -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"student\",\"password\":\"123456\"}"
```

在 Knife4j 页面调试更省事：点右上角 **Authorize**，填入 `Bearer <token>` 即可调用受保护接口。

---

## 三、常见问题

**端口 8080 被占用 / 启动报 `Port already in use`**

```bash
netstat -ano | findstr :8080
taskkill /PID <pid> /F
```

**`mvn package` 报 `Unable to rename ... .jar to .jar.original`**

应用还在运行，jar 被文件锁占用。先停掉程序再打包——这个错在开发中很常见，不是代码问题。

**连不上 MySQL**

先看 `docker compose ps` 是否 healthy。数据库表由 Flyway 自动创建（`spring.flyway.baseline-on-migrate=true`），不需要手动执行 DDL。

**改了表结构但没生效**

Flyway 只执行未应用过的版本。新增变更请加 `V2__xxx.sql` 这样的新文件，**不要修改已执行过的 V1**，否则校验和不匹配会启动失败。

**控制台中文乱码**

源文件统一 UTF-8；Windows 控制台可先 `chcp 65001`。若用 PowerShell 运行脚本，`.ps1` 必须存为 **UTF-8 with BOM**，否则 5.1 会按 GBK 解析导致语法错误。

**首次构建很慢**

依赖走阿里云镜像（`~/.m2/settings.xml`），本地仓库在 `D:\develop\maven\repository`。首次约 1~2 分钟，之后增量构建在 10 秒级。
