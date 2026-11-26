package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师端答卷详情：比学生成绩单多出「判分依据」与「作答记录 ID」，
 * 用于主观题复核时判断机器为什么给这个分，以及定位要修改哪一行。
 */
@Data
@Builder
public class AttemptDetailVO {

    private Long attemptId;

    private Long examId;

    private String examTitle;

    private Long userId;

    private String username;

    private String realName;

    private String status;

    private LocalDateTime submitTime;

    private BigDecimal objectiveScore;

    private BigDecimal subjectiveScore;

    private BigDecimal totalScore;

    private BigDecimal passScore;

    private Integer isPass;

    /** 仍有待人工复核的简答题 */
    private boolean needReview;

    /** 待复核题数：复核完为 0，前端据此提示"已全部复核" */
    private int pendingReviewCount;

    private List<AnswerDetail> answers;

    @Data
    @Builder
    public static class AnswerDetail {
        /** attempt_answer 主键，复核接口用它定位 */
        private Long answerId;
        private Long questionId;
        private Integer sortNo;
        private String type;
        private String typeLabel;
        private String stem;
        private List<AttemptResultVO.Option> options;
        /** 考生作答 */
        private List<String> myAnswer;
        private String correctAnswerText;
        /** 当前得分（元），未判分时为 0 */
        private BigDecimal score;
        private BigDecimal fullScore;
        /** AUTO / MANUAL / UNJUDGED */
        private String judgeType;
        /** 机器判分依据，如"命中关键词 2/3: 可达性分析,分代" */
        private String judgeDetail;
    }
}
