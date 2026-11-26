package com.example.oes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oes.entity.Question;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 批量查询已存在的题干指纹。走 uk_stem_hash 覆盖索引。
     * 供导入时先做预检，避免逐条 insert 触发唯一键异常。
     */
    @Select("""
            <script>
            SELECT stem_hash FROM question
            WHERE stem_hash IN
            <foreach collection="hashes" item="h" open="(" separator="," close=")">#{h}</foreach>
            </script>
            """)
    List<String> selectExistingHashes(@Param("hashes") Collection<String> hashes);

    /**
     * 按分类统计题目数。一次聚合替代"逐个分类 count"的 N+1 写法。
     */
    @Select("SELECT category_id AS categoryId, COUNT(*) AS cnt FROM question GROUP BY category_id")
    List<Map<String, Object>> countGroupByCategory();

    /**
     * 批量插入。一条 SQL 带 500 组 VALUES，把往返次数从 O(n) 降到 O(n/500)。
     * 依赖 JDBC 参数 rewriteBatchedStatements 不生效于此写法，但单条多值本身已是最快路径。
     */
    @Insert("""
            <script>
            INSERT INTO question
                (category_id, type, stem, stem_hash, options_json, answer_json,
                 analysis, difficulty, default_score, status, created_by)
            VALUES
            <foreach collection="list" item="q" separator=",">
                (#{q.categoryId}, #{q.type}, #{q.stem}, #{q.stemHash}, #{q.optionsJson},
                 #{q.answerJson}, #{q.analysis}, #{q.difficulty}, #{q.defaultScore},
                 #{q.status}, #{q.createdBy})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<Question> list);
}
