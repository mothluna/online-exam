-- 首次启动时由 mysql 镜像的 /docker-entrypoint-initdb.d 自动执行（仅当数据卷为空）
-- 用途：统一字符集、创建应用账号、写入初始化标记

SET NAMES utf8mb4;

-- 应用账号：业务代码禁止用 root 连接
CREATE USER IF NOT EXISTS 'oes'@'%' IDENTIFIED BY 'oes123456';
GRANT ALL PRIVILEGES ON oes.* TO 'oes'@'%';
FLUSH PRIVILEGES;

-- 初始化标记表，用于验证容器是否完成首次初始化
CREATE TABLE IF NOT EXISTS schema_version (
  version     VARCHAR(32)  NOT NULL,
  description VARCHAR(200) NOT NULL,
  applied_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO schema_version (version, description)
VALUES ('V0', 'bootstrap: utf8mb4 + app user oes')
ON DUPLICATE KEY UPDATE description = VALUES(description);
