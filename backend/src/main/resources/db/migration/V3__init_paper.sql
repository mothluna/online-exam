-- V3: 试卷、抽题规则、试卷题目
-- 设计要点：
--   1. paper_question 存分值快照：题库里题目默认分改了，历史试卷不受影响
--   2. uk_paper_question 保证同一试卷内题目不重复，是"抽题不重复"的最终防线
--   3. paper.total_score 由规则推导，发布前与实际汇总值做事实校验

CREATE TABLE IF NOT EXISTS paper (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200) NOT NULL,
    compose_type VARCHAR(16)  NOT NULL COMMENT 'MANUAL 手工 / RANDOM 按规则抽题',
    total_score  INT          NOT NULL DEFAULT 0 COMMENT '总分，单位 0.01 分',
    total_count  INT          NOT NULL DEFAULT 0,
    pass_score   INT          NOT NULL DEFAULT 0 COMMENT '及格分，单位 0.01 分',
    duration_sec INT          NOT NULL COMMENT '考试时长（秒）',
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    created_by   BIGINT       NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_creator_status (created_by, status),
    KEY idx_status (status, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='试卷';

CREATE TABLE IF NOT EXISTS paper_rule (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    paper_id       BIGINT      NOT NULL,
    type           VARCHAR(16) NOT NULL,
    question_count INT         NOT NULL COMMENT '抽题数量',
    score_per_item INT         NOT NULL COMMENT '每题分值，单位 0.01 分',
    category_ids   VARCHAR(500) NULL COMMENT '限定分类，逗号分隔；NULL 表示不限',
    difficulty     TINYINT     NULL COMMENT '限定难度；NULL 表示不限',
    sort_no        INT         NOT NULL DEFAULT 0 COMMENT '题型展示顺序',
    PRIMARY KEY (id),
    KEY idx_paper (paper_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='抽题规则';

CREATE TABLE IF NOT EXISTS paper_question (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    paper_id    BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    score       INT    NOT NULL COMMENT '本题分值快照，单位 0.01 分',
    sort_no     INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_question (paper_id, question_id),
    KEY idx_paper_sort (paper_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='试卷题目';
