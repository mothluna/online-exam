package com.example.oes.mapper;

import com.example.oes.dto.AttemptExportRow;
import com.example.oes.vo.AttemptStatVO;
import com.example.oes.vo.ExamStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 统计专用查询。全部是"一次拿一页"的聚合，禁止出现按行循环查库的写法。
 */
@Mapper
public interface StatMapper {

    /**
     * 本页考试的聚合指标。一次扫描同时算出：开考数、提交数、平均/最高/最低分、及格数。
     * 用 CASE WHEN 而不是子查询，避免同一批数据被扫多遍。
     */
    @Select("""
            <script>
            SELECT exam_id AS examId,
                   COUNT(*) AS joinCount,
                   SUM(status &lt;&gt; 'IN_PROGRESS') AS submitCount,
                   ROUND(AVG(CASE WHEN status &lt;&gt; 'IN_PROGRESS' THEN total_score END)) AS avgScore,
                   MAX(CASE WHEN status &lt;&gt; 'IN_PROGRESS' THEN total_score END) AS maxScore,
                   MIN(CASE WHEN status &lt;&gt; 'IN_PROGRESS' THEN total_score END) AS minScore,
                   SUM(status &lt;&gt; 'IN_PROGRESS' AND is_pass = 1) AS passCount
            FROM exam_attempt
            WHERE exam_id IN
            <foreach collection="examIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY exam_id
            </script>
            """)
    List<Map<String, Object>> aggregateByExams(@Param("examIds") Collection<Long> examIds);

    @Select("SELECT COUNT(*) FROM exam_attempt WHERE exam_id = #{examId}")
    long countAttempts(@Param("examId") Long examId);

    /**
     * 考试分页。与试卷总分一次 JOIN 取出，避免"先查考试再批量查试卷"多一条 SQL ——
     * 这样整个统计列表严格是 3 条：count + 分页 + 聚合。
     */
    @Select("""
            SELECT e.id AS examId,
                   e.title AS title,
                   e.status AS status,
                   e.start_time AS startTime,
                   e.end_time AS endTime,
                   p.total_score / 100 AS paperTotalScore
            FROM exam e
            LEFT JOIN paper p ON p.id = e.paper_id
            WHERE (#{status,jdbcType=VARCHAR} IS NULL OR e.status = #{status,jdbcType=VARCHAR})
              AND (#{keyword,jdbcType=VARCHAR} IS NULL OR e.title LIKE CONCAT('%', #{keyword,jdbcType=VARCHAR}, '%'))
            ORDER BY e.id DESC
            LIMIT #{offset}, #{size}
            """)
    List<ExamStatVO> pageExams(@Param("status") String status,
                               @Param("keyword") String keyword,
                               @Param("offset") long offset,
                               @Param("size") long size);

    @Select("""
            SELECT COUNT(*) FROM exam e
            WHERE (#{status,jdbcType=VARCHAR} IS NULL OR e.status = #{status,jdbcType=VARCHAR})
              AND (#{keyword,jdbcType=VARCHAR} IS NULL OR e.title LIKE CONCAT('%', #{keyword,jdbcType=VARCHAR}, '%'))
            """)
    long countExams(@Param("status") String status, @Param("keyword") String keyword);

    @Select("""
            SELECT a.id AS attemptId,
                   a.user_id AS userId,
                   u.real_name AS realName,
                   a.status AS status,
                   a.total_score / 100 AS totalScore,
                   a.is_pass AS isPass,
                   a.submit_time AS submitTime,
                   TIMESTAMPDIFF(SECOND, a.start_time, COALESCE(a.submit_time, a.deadline)) AS durationSec
            FROM exam_attempt a
            LEFT JOIN sys_user u ON u.id = a.user_id
            WHERE a.exam_id = #{examId}
            ORDER BY a.total_score DESC, a.id
            LIMIT #{offset}, #{size}
            """)
    List<AttemptStatVO> pageAttempts(@Param("examId") Long examId,
                                     @Param("offset") long offset,
                                     @Param("size") long size);

    /**
     * 导出取数。与明细分页同样的排序与口径，状态与及格在 SQL 里就转成中文，
     * 避免导出文件里出现 GRADED 这种内部枚举值。
     */
    @Select("""
            SELECT a.id AS attemptId,
                   u.username AS username,
                   u.real_name AS realName,
                   CASE a.status
                       WHEN 'GRADED' THEN '已判分'
                       WHEN 'SUBMITTED' THEN '已交卷'
                       WHEN 'TIMEOUT' THEN '超时交卷'
                       ELSE '进行中'
                   END AS statusText,
                   a.total_score / 100 AS totalScore,
                   IF(a.is_pass = 1, '及格', '不及格') AS passText,
                   a.submit_time AS submitTime,
                   TIMESTAMPDIFF(SECOND, a.start_time, COALESCE(a.submit_time, a.deadline)) AS durationSec
            FROM exam_attempt a
            LEFT JOIN sys_user u ON u.id = a.user_id
            WHERE a.exam_id = #{examId}
            ORDER BY a.total_score DESC, a.id
            LIMIT #{offset}, #{size}
            """)
    List<AttemptExportRow> exportRows(@Param("examId") Long examId,
                                      @Param("offset") long offset,
                                      @Param("size") long size);
}
