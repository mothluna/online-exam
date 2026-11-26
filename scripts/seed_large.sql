-- 为大导出场景追加答卷：exam 1 补到 6001 条，触发 >5000 行的异步分支
-- 必须声明连接字符集：mysql 客户端默认 latin1，中文会被双重编码成乱码
SET NAMES utf8mb4;

SET SESSION cte_max_recursion_depth = 10000;

INSERT IGNORE INTO exam_attempt (exam_id, user_id, paper_id, seq_no, status, start_time, deadline,
                          submit_time, objective_score, subjective_score, total_score, is_pass, version, created_at)
WITH RECURSIVE seq AS (SELECT 2001 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 7000)
SELECT 1, 50000 + n, 1, 1, 'GRADED',
       NOW() - INTERVAL 4 HOUR, NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 200 MINUTE,
       (n * 6151) % 10000, 0, (n * 6151) % 10000,
       IF((n * 6151) % 10000 >= 6000, 1, 0), 0, NOW() - INTERVAL 4 HOUR
FROM seq;

-- 5. 补齐压测答卷对应的考生账号，否则导出时用户名/姓名为空
INSERT INTO sys_user (id, username, password, real_name, role_code, status, auth_version)
WITH RECURSIVE r1 AS (SELECT 20401 AS n UNION ALL SELECT n + 1 FROM r1 WHERE n < 22150),
               r2 AS (SELECT 52001 AS n UNION ALL SELECT n + 1 FROM r2 WHERE n < 57000)
SELECT n, CONCAT('stu_', n),
       (SELECT password FROM sys_user WHERE username = 'student'),
       CONCAT('考生', n), 'STUDENT', 1, 0
FROM (SELECT n FROM r1 UNION ALL SELECT n FROM r2) t
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name);
