-- 填空题的空数由数据决定，不再靠前端数题干里的下划线
--
-- 背景：原实现用 `/_{2,}/g` 数题干下划线来渲染输入框，题干用"（ ）"或没有下划线时会识别错。
-- 空数的真正来源是答案数组的长度（一个空对应一个答案），所以直接存下来。
-- 非填空题该列恒为 1，保持 NOT NULL 便于读取，不用到处判空。

ALTER TABLE question
    ADD COLUMN blank_count TINYINT NOT NULL DEFAULT 1 COMMENT '填空题空数；非填空题恒为 1' AFTER answer_json;

-- 历史数据回填：按答案数组长度修正，至少 1
UPDATE question
SET blank_count = GREATEST(1, JSON_LENGTH(answer_json))
WHERE type = 'BLANK'
  AND JSON_VALID(answer_json);
