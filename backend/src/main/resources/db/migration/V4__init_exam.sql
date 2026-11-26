-- V4: 考试、答卷、作答明细
-- 设计要点：
--   1. exam_attempt 唯一键为 (exam_id, user_id, seq_no)：
--      allow_retake=0 时永远插入 seq_no=1，第二次插入即撞唯一键 → 由数据库仲裁"不可重复开考"
--      allow_retake=1 时 seq_no 递增，允许重考。若只用 (exam_id,user_id) 则无法支持重考
--   2. deadline 由服务端计算并落库，前端倒计时仅用于展示
--   3. attempt_answer 的 (attempt_id, question_id) 唯一，保证暂存幂等

CREATE TABLE IF NOT EXISTS exam (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    paper_id     BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    start_time   DATETIME     NOT NULL,
    end_time     DATETIME     NOT NULL,
    duration_sec INT          NOT NULL COMMENT '单场考试时长（秒）',
    allow_retake TINYINT      NOT NULL DEFAULT 0,
    shuffle      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否打乱题目顺序',
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / CLOSED',
    created_by   BIGINT       NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_time (status, start_time, end_time),
    KEY idx_paper (paper_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='考试';

CREATE TABLE IF NOT EXISTS exam_attempt (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    exam_id          BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    paper_id         BIGINT      NOT NULL,
    seq_no           INT         NOT NULL DEFAULT 1 COMMENT '第几次作答，重考时递增',
    status           VARCHAR(16) NOT NULL COMMENT 'IN_PROGRESS / SUBMITTED / TIMEOUT / GRADED',
    start_time       DATETIME(3) NOT NULL,
    deadline         DATETIME(3) NOT NULL COMMENT '服务端计算的截止时间',
    submit_time      DATETIME(3) NULL,
    objective_score  INT         NOT NULL DEFAULT 0 COMMENT '单位 0.01 分',
    subjective_score INT         NOT NULL DEFAULT 0,
    total_score      INT         NOT NULL DEFAULT 0,
    is_pass          TINYINT     NOT NULL DEFAULT 0,
    version          INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_user_seq (exam_id, user_id, seq_no),
    KEY idx_status_deadline (status, deadline),
    KEY idx_exam_status_score (exam_id, status, total_score),
    KEY idx_user_time (user_id, submit_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='答卷';

CREATE TABLE IF NOT EXISTS attempt_answer (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    attempt_id   BIGINT       NOT NULL,
    question_id  BIGINT       NOT NULL,
    answer_json  JSON         NULL COMMENT '考生答案',
    score        INT          NOT NULL DEFAULT 0 COMMENT '得分，单位 0.01 分',
    judge_type   VARCHAR(16)  NOT NULL DEFAULT 'UNJUDGED' COMMENT 'AUTO / MANUAL / UNJUDGED',
    judge_detail VARCHAR(500) NULL COMMENT '判分依据，如命中的关键词',
    sort_no      INT          NOT NULL DEFAULT 0 COMMENT '本卷内展示顺序',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt_question (attempt_id, question_id),
    KEY idx_attempt_sort (attempt_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='作答明细';
