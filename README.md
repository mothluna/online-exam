# 在线考试系统（OES）

面向学校/培训机构的小型在线考试系统，覆盖「建题库 → 组卷 → 组织考试 → 作答 → 自动判卷 → 成绩统计」闭环。

后端 Java 17 + Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis + S3 兼容对象存储（RustFS，MinIO SDK 直连）；
前端 Vue 3 + Element Plus + Vite + Pinia + Vue Router。

```
docs/      需求分析与方案设计、环境检查报告、开发日志、测试报告
backend/   Spring Boot 后端（单模块，按 controller/service/mapper 分包）
frontend/  Vue 3 前端（教师端 5 页 + 学生端 3 页）
deploy/    docker-compose、初始化 SQL、环境自检脚本
```

---

## 一、当前进度

| 模块 | 后端 | 前端 | 说明 |
|---|---|---|---|
| 认证鉴权 | ✅ | ✅ | JWT + Redis 白名单 + 版本号；401/403 流水线；登录 5 次锁定 |
| 用户管理 | ✅ | ✅ | 增删改查、重置密码、启停、强制下线；三条自保护 |
| 题库与分类 | ✅ | ✅ | 五类题型、三级分类树、题干归一化去重 |
| Excel 批量导入 | ✅ | ✅ | 1000 行约 0.7s，失败明细回传 |
| 试卷与组卷 | ✅ | ✅ | 手工/按比例随机、抽题不重复、总分零偏差、发布前事实校验 |
| 考试与作答 | ✅ | ✅ | 防重复开考、批量暂存幂等、服务端计时、超时自动交卷 |
| 自动判卷 | ✅ | — | 客观题自动判分（整数分值），主观题关键词粗筛 |
| 成绩统计与导出 | ✅ | ✅ | 固定 3 条 SQL、分数分布、同步/异步导出 |
| 学生成绩查看 | ✅ | ✅ | 列表直接显示得分，逐题回顾；考试结束前锁定答案 |
| 主观题人工复核 | ✅ | ✅ | 教师按题改分并重算总分与及格；统计页「复核」抽屉直达答卷 |

细节与取舍见 `docs/02-开发日志.md`；需求与设计见 `docs/01-需求分析与方案设计.md`。

---

## 二、本地运行

### 0. 前置条件

| 项 | 要求 | 本机状态 |
|---|---|---|
| JDK | 17 | `D:\develop\jdk\current` 已指向 `jdk-17.0.2` |
| Docker Desktop | 运行中 | 需手动启动（引擎依赖 wsl.exe） |
| 中间件 | MySQL / Redis / 对象存储 | 见下方命令 |

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

### 4. 前端（Vue 3 + Vite）

```bash
cd frontend
npm install --registry=https://registry.npmmirror.com --cache .npm-cache
npm run dev          # http://localhost:5173
```

开发期由 Vite 把 `/api` 代理到 `http://localhost:8080`，**后端必须先启动**。

生产构建（Windows 需要把临时目录指到项目内，见常见问题）：

```bash
cd frontend
mkdir -p .tmp
TMP=$PWD/.tmp TEMP=$PWD/.tmp npm run build
```

### 5. 运行测试

```bash
cd backend
mvn test
```

集成测试需要本机 MySQL / Redis 已启动：自动创建并迁移 `oes_test` 库，Redis 使用 **DB 15**，
与开发数据隔离；用例在事务内执行并回滚，可重复运行。详见 `docs/04-测试报告.md`。

---

## 三、验证清单

启动后端与前端后按顺序执行，全部通过说明环境与代码都正常。

| # | 操作 | 期望 |
|---|---|---|
| 1 | `GET http://localhost:8080/api/ping` | `200`，`{"code":0,...,"java":"17.0.2"}` |
| 2 | 打开 http://localhost:8080/doc.html | Knife4j 文档页，标题为「在线考试系统 API」，左侧按 `00-系统` ~ `09-主观题复核` 共 10 个分组列出 53 个接口；右上角 Authorize 可填 token |
| 3 | `POST /api/auth/login`（student / 123456） | `200`，返回 token、角色与用户信息 |
| 4 | `GET /api/auth/me`（带 token） | `200` |
| 5 | `GET /api/admin/questions`（学生 token） | **403 / 40300** |
| 6 | `POST /api/auth/logout` 后复用旧 token | **401 / 40102** |
| 7 | 教师：`POST /api/admin/questions/import` | `200`，返回 success / failed 与失败明细 |
| 8 | 教师：创建试卷 → `POST /api/admin/papers/{id}/randomize` | `200`，抽题数与总分等于规则合计 |
| 9 | 学生：`POST /api/student/exams/{id}/start` → 暂存 → `submit` | `200`，返回得分与是否及格 |
| 10 | 学生：`GET /api/student/attempts/{id}/result` | 总分可见；考试未结束时 `answerVisible=false`（答案与逐题得分不泄露） |
| 11 | 教师：`GET /api/admin/stats/exams` | `200`，返回开考/提交/均分/及格率 |
| 12 | 打开 http://localhost:5173 | 登录后按角色显示菜单；学生端「我的考试」可看分与逐题回顾 |
| 13 | 教师：记录统计 → 成绩明细 →「复核」 | 给简答题改分后总分与及格状态即时更新，学生成绩页不再提示待复核 |

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

## 四、部署

- **本机开发**：中间件 `cd deploy && docker compose up -d`，后端 `mvn spring-boot:run`，前端 `npm run dev`。
- **一键部署**：`cd deploy && docker compose --profile app up -d --build`（api + web + 中间件），入口 `http://localhost:8081`。
- 生产配置、备份、升级、安全清单见 [`docs/05-部署手册.md`](docs/05-部署手册.md)。

---

## 五、已知限制

- **主观题自动评分只做粗筛**：简答题按关键词命中给分并标记待复核，最终成绩以教师复核为准；复核入口在「记录统计 → 成绩明细 → 复核」。
- **暂存不校验答案取值**：如判断题传 `"B"` 会被原样存下、判 0 分（考试链路优先「不阻断考生」）。
- **填空题空数靠题干下划线推断**：题干无下划线时按 1 空渲染。
- **同一考生多标签页作答**：自动暂存互相覆盖，后写胜。
- **前端已改按需引入**：Element Plus 只打包实际用到的 44 个组件，全站 gzip 由 513.7KB 降到 371.8KB（−27.6%）。模板里的 `el-*` 由 `unplugin-vue-components` 自动解析，新增组件无需手动注册；但**函数式组件（`ElMessage`/`ElMessageBox`）的样式**与**字符串形式的图标引用**不会被自动处理，新增时注意（见 `docs/02-开发日志.md` D-038）。
- **单实例假设**：定时任务未接 ShedLock，导出任务存内存（重启丢失）。
- **测试**：`mvn test` 共 38 个自动化测试（判分/归一化/分值单测 + 3 个接口集成用例）；性能压测仍为手工脚本，未接入 CI，详见 `docs/04-测试报告.md`。
- P-11 ~ P-22 等联调遗留项见 `docs/02-开发日志.md` 第 3.2 节。

---

## 六、常见问题

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

Flyway 只执行未应用过的版本。新增变更请加 `V5__xxx.sql` 这样的新文件，**不要修改已执行过的 V1~V4**，否则校验和不匹配会启动失败。

**控制台中文乱码**

源文件统一 UTF-8；Windows 控制台可先 `chcp 65001`。若用 PowerShell 运行脚本，`.ps1` 必须存为 **UTF-8 with BOM**，否则 5.1 会按 GBK 解析导致语法错误。

**首次构建很慢**

依赖走阿里云镜像（`~/.m2/settings.xml`），本地仓库在 `D:\develop\maven\repository`。首次约 1~2 分钟，之后增量构建在 10 秒级。

**前端 `npm install` 报 `EPERM` / 缓存目录无权限**

默认缓存在 `D:\develop\node\node_cache`，当前用户不可写。加 `--cache .npm-cache` 把缓存放到项目内。

**前端 `vite build` 报 `Access is denied`（esbuild 临时文件）**

esbuild 会往系统临时目录写文件，本机被权限拦住。把 `TMP`/`TEMP` 指到项目内：

```bash
mkdir -p .tmp
TMP=$PWD/.tmp TEMP=$PWD/.tmp npm run build
```

**`vite build` 二次构建报 `SAFE_DELETE_BULK_CONFIRM_REQUIRED`**

环境的安全删除保护拦截批量删除 `dist/assets`。先 `git clean -fdxq dist` 再构建。
