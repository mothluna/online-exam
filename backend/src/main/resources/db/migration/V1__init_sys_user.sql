-- V1: 用户与角色
-- 说明：题目要求"角色区分"，本系统角色互斥且稳定，故用 role_code 单字段，
--       不建 user_role 关联表（详见设计文档 2.4.4）。

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    real_name     VARCHAR(50)  NOT NULL COMMENT '姓名',
    role_code     VARCHAR(20)  NOT NULL COMMENT 'ADMIN / TEACHER / STUDENT',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    auth_version  INT          NOT NULL DEFAULT 0 COMMENT '权限版本，改密/改角色时 +1，令旧令牌全部失效',
    last_login_at DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role_status (role_code, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';
