-- 压测数据：1000 名考生 + 1000 份答卷 + 18 场考试（每场 50 份）
-- 用于验证"1000 条数据响应 ≤ 1 秒"与"SQL 条数为常数级"
-- 分数用确定性表达式生成（而非 RAND），保证 objective_score 与 total_score 一致且可复现
-- 注意：仅在开发库执行，勿在生产运行

SET SESSION cte_max_recursion_depth = 5000;

-- 1. 1000 个考生账号，密码与 student 相同（123456），便于直接登录验证
INSERT INTO sys_user (id, username, password, real_name, role_code, status, auth_version)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 1000)
SELECT 10000 + n,
       CONCAT('stu', n),
       (SELECT password FROM sys_user WHERE username = 'student'),
       CONCAT('考生', n),
       'STUDENT', 1, 0
FROM seq;

-- 2. exam 1 造 1000 份答卷：总分 0~99.99，60 分及以上及格
INSERT INTO exam_attempt (exam_id, user_id, paper_id, seq_no, status, start_time, deadline,
                          submit_time, objective_score, subjective_score, total_score, is_pass, version, created_at)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 1000)
SELECT 1, 10000 + n, 1, 1, 'GRADED',
       NOW() - INTERVAL 2 HOUR,
       NOW() - INTERVAL 90 MINUTE,
       NOW() - INTERVAL 95 MINUTE,
       (n * 7919) % 10000, 0, (n * 7919) % 10000,
       IF((n * 7919) % 10000 >= 6000, 1, 0), 0, NOW() - INTERVAL 2 HOUR
FROM seq;

-- 3. 追加 18 场考试（id 4~21），用于验证聚合列表不随场次增长而退化
INSERT INTO exam (paper_id, title, start_time, end_time, duration_sec, allow_retake, shuffle, status, created_by)
WITH RECURSIVE seq AS (SELECT 4 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 21)
SELECT 1, CONCAT('压测考试 ', n), NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 1 DAY,
       3600, 0, 0, 'PUBLISHED', 1
FROM seq;

-- 4. 每场 50 份答卷，共 900 份
INSERT INTO exam_attempt (exam_id, user_id, paper_id, seq_no, status, start_time, deadline,
                          submit_time, objective_score, subjective_score, total_score, is_pass, version, created_at)
WITH RECURSIVE exam_seq AS (SELECT 4 AS e UNION ALL SELECT e + 1 FROM exam_seq WHERE e < 21),
               user_seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM user_seq WHERE n < 50)
SELECT e.e, 20000 + e.e * 100 + u.n, 1, 1, 'GRADED',
       NOW() - INTERVAL 3 HOUR,
       NOW() - INTERVAL 2 HOUR,
       NOW() - INTERVAL 130 MINUTE,
       (e.e * 7919 + u.n * 104729) % 10000, 0, (e.e * 7919 + u.n * 104729) % 10000,
       IF((e.e * 7919 + u.n * 104729) % 10000 >= 6000, 1, 0), 0, NOW() - INTERVAL 3 HOUR
FROM exam_seq e
CROSS JOIN user_seq u;
