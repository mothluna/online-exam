package com.example.oes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oes.entity.ExamAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExamAttemptMapper extends BaseMapper<ExamAttempt> {

    @Select("""
            SELECT * FROM exam_attempt
            WHERE exam_id = #{examId} AND user_id = #{userId}
            ORDER BY seq_no DESC LIMIT 1
            """)
    ExamAttempt selectLatest(@Param("examId") Long examId, @Param("userId") Long userId);

    @Select("SELECT COALESCE(MAX(seq_no), 0) FROM exam_attempt WHERE exam_id = #{examId} AND user_id = #{userId}")
    int selectMaxSeqNo(@Param("examId") Long examId, @Param("userId") Long userId);

    /**
     * 状态跃迁用 CAS：只有 IN_PROGRESS 才能改成 SUBMITTED。
     * 并发重复提交时，第二次 affected=0，据此返回既有结果而不重复判分。
     */
    @Update("""
            UPDATE exam_attempt
            SET status = #{targetStatus}, submit_time = #{submitTime}, version = version + 1
            WHERE id = #{id} AND status = 'IN_PROGRESS' AND version = #{version}
            """)
    int casSubmit(@Param("id") Long id,
                  @Param("targetStatus") String targetStatus,
                  @Param("submitTime") LocalDateTime submitTime,
                  @Param("version") int version);

    /** 超时自动交卷：不依赖 version，但必须仍处于 IN_PROGRESS */
    @Update("""
            UPDATE exam_attempt
            SET status = 'TIMEOUT', submit_time = deadline, version = version + 1
            WHERE id = #{id} AND status = 'IN_PROGRESS'
            """)
    int casTimeout(@Param("id") Long id);

    @Select("""
            SELECT id FROM exam_attempt
            WHERE status = 'IN_PROGRESS' AND deadline < NOW(3)
            ORDER BY deadline LIMIT #{limit}
            """)
    List<Long> selectExpiredIds(@Param("limit") int limit);

    /** 一次聚合拿到本页考试的参考人数，避免逐场 count 的 N+1 */
    @Select("""
            <script>
            SELECT exam_id AS examId, COUNT(*) AS cnt FROM exam_attempt
            WHERE exam_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY exam_id
            </script>
            """)
    List<java.util.Map<String, Object>> countByExamIds(@Param("ids") java.util.Collection<Long> ids);
}
