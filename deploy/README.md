# 本地开发环境（deploy）

一条命令起 MySQL 8 + Redis 7 + 对象存储，宿主机零安装。

## 启动

```bash
cd deploy
cp .env.example .env      # 首次
docker compose up -d      # 只起中间件（本地开发）
```

一键部署应用层（api + web + 中间件）：

```bash
docker compose --profile app up -d --build
# 入口 http://localhost:8081，后端 8080
```

完整部署、备份与上线检查见 `docs/05-部署手册.md`。

自检（Windows PowerShell）：

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\check-env.ps1
```

## 端口与账号

| 服务 | 端口 | 账号 / 口令 | 说明 |
|---|---|---|---|
| MySQL 8.0.36 | 3306 | root / root123456<br>oes / oes123456 | 库 `oes`，utf8mb4_0900_ai_ci |
| Redis 7.2 | 6379 | 口令 redis123456 | AOF 持久化开启 |
| 对象存储（S3） | 9000 | minioadmin / minio123456 | 控制台 9001 |
| 控制台 | 9001 | 同上 | http://localhost:9001/rustfs/console/ |

> PostgreSQL 占用的 5432 与本套件无交集，可同时运行。

## 关于对象存储的说明

课题要求 MinIO，但 MinIO 开源版已停止分发：

- `https://dl.min.io/server/minio/...` 返回 **410 Gone**（项目已归档）
- Docker Hub / 各镜像源的 `minio/minio` 均不可拉取

因此本项目底层改用 **RustFS**——S3 协议完全兼容，**Java 侧仍使用 MinIO SDK（io.minio）**，只改 endpoint，业务代码零差异。

将来若拿到 MinIO 镜像，只需在 `.env` 里改一行：

```
OBJECT_STORAGE_IMAGE=minio/minio:latest
```

## 常用命令

```bash
docker compose ps                 # 状态
docker compose logs -f mysql      # 日志
docker compose stop               # 停止（保留数据）
docker compose down               # 删除容器（保留数据卷）
docker compose down -v            # 连数据卷一起删，彻底重来
```

## 数据卷

| 卷 | 用途 |
|---|---|
| `oes_mysql-data` | MySQL 数据目录 |
| `oes_redis-data` | Redis AOF |
| `oes_minio-data` | 对象存储数据 |

首次启动（数据卷为空）会自动执行 `sql/01-bootstrap.sql`：统一字符集、创建应用账号 `oes`、写入 `schema_version` 标记。**数据卷已存在时该脚本不会重跑**，需要重来时用 `down -v`。

## 已知问题

**镜像拉取失败**：守护进程配置的国内镜像源不稳定（`xuanyuan` 常报"节点繁忙"）。可绕开镜像源单独拉取后重命名：

```bash
docker pull docker.m.daocloud.io/library/mysql:8.0.36
docker tag  docker.m.daocloud.io/library/mysql:8.0.36 mysql:8.0.36
```

**脚本乱码**：Windows PowerShell 5.1 默认按 GBK 读取无 BOM 的 .ps1，中文会乱码并引发语法错误。所有 .ps1 必须存为 UTF-8 with BOM。

**数据卷放在 bind mount 上会变慢**：本配置一律使用命名卷，不要改成挂载 Windows 目录（`/mnt/d/...`），否则批量导入的 IO 会慢 5~10 倍，直接影响"1000 条 ≤30 秒"指标。
