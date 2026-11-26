package com.example.oes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oes.entity.PaperQuestion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PaperQuestionMapper extends BaseMapper<PaperQuestion> {

    /**
     * 试卷题目的"事实值"。发布前用它和 paper.total_score 对账，
     * 一条 SQL 同时拿到条数与分值和，避免两次查询之间的并发窗口。
     */
    @Select("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(score), 0) AS sumScore
            FROM paper_question WHERE paper_id = #{paperId}
            """)
    Map<String, Object> countAndSum(@Param("paperId") Long paperId);

    @Delete("DELETE FROM paper_question WHERE paper_id = #{paperId}")
    int deleteByPaperId(@Param("paperId") Long paperId);

    @Select("SELECT question_id FROM paper_question WHERE paper_id = #{paperId}")
    List<Long> selectQuestionIds(@Param("paperId") Long paperId);

    @Insert("""
            <script>
            INSERT INTO paper_question (paper_id, question_id, score, sort_no) VALUES
            <foreach collection="list" item="i" separator=",">
                (#{i.paperId}, #{i.questionId}, #{i.score}, #{i.sortNo})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<PaperQuestion> list);
}
