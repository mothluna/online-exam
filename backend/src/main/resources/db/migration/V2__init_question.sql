-- V2: 题目分类与题目
-- 设计要点：
--   1. 题目用 status 停用替代删除，因为逻辑删除会与 stem_hash 唯一索引冲突
--      （多条 deleted=1 的记录 + 唯一索引 = 插入失败），且历史答卷需要还原题干
--   2. stem_hash 存归一化题干的 SHA-256，是重复拦截的最终防线

CREATE TABLE IF NOT EXISTS question_category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '0 表示根节点',
    name       VARCHAR(50) NOT NULL,
    sort_no    INT         NOT NULL DEFAULT 0,
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parent_name (parent_id, name),
    KEY idx_parent_sort (parent_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='题目分类';

CREATE TABLE IF NOT EXISTS question (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    category_id   BIGINT      NOT NULL,
    type          VARCHAR(16) NOT NULL COMMENT 'SINGLE/MULTIPLE/JUDGE/BLANK/SHORT',
    stem          TEXT        NOT NULL COMMENT '题干原文',
    stem_hash     CHAR(64)    NOT NULL COMMENT '归一化题干 SHA-256',
    options_json  JSON        NULL COMMENT '[{"key":"A","content":"..."}]',
    answer_json   JSON        NOT NULL COMMENT '["A"] / ["A","C"] / ["正确"]',
    analysis      TEXT        NULL COMMENT '解析',
    difficulty    TINYINT     NOT NULL DEFAULT 2 COMMENT '1 易，2 中，3 难',
    default_score INT         NOT NULL COMMENT '默认分值，单位 0.01 分',
    status        TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
    created_by    BIGINT      NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stem_hash (stem_hash),
    KEY idx_cat_type_status (category_id, type, status, id),
    KEY idx_type_diff (type, difficulty, status, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='题目';
