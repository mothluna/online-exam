package com.example.oes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oes.entity.Question;
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
}
