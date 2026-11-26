package com.example.oes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oes.entity.AttemptAnswer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttemptAnswerMapper extends BaseMapper<AttemptAnswer> {

    @Select("SELECT * FROM attempt_answer WHERE attempt_id = #{attemptId} ORDER BY sort_no")
    List<AttemptAnswer> selectByAttempt(@Param("attemptId") Long attemptId);

    /** 开考时按试卷结构生成空答卷行，顺序固定下来便于续考与统计 */
    @Insert("""
            <script>
            INSERT INTO attempt_answer (attempt_id, question_id, score, judge_type, sort_no) VALUES
            <foreach collection="list" item="i" separator=",">
                (#{i.attemptId}, #{i.questionId}, 0, 'UNJUDGED', #{i.sortNo})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<AttemptAnswer> list);

    /**
     * 逐题/批量暂存。唯一键冲突时覆盖答案，天然幂等：
     * 前端每 10 秒重发同一批数据不会产生重复行，也不会报错。
     */
    @Insert("""
            <script>
            INSERT INTO attempt_answer (attempt_id, question_id, answer_json, score, judge_type, sort_no)
            VALUES
            <foreach collection="list" item="i" separator=",">
                (#{i.attemptId}, #{i.questionId}, #{i.answerJson}, 0, 'UNJUDGED', #{i.sortNo})
            </foreach>
            ON DUPLICATE KEY UPDATE
                answer_json = VALUES(answer_json),
                updated_at = NOW(3)
            </script>
            """)
    int upsertBatch(@Param("list") List<AttemptAnswer> list);
}
