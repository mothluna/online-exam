package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考生成绩单。
 *
 * <p>关键设计：正确答案**不在交卷后立即下发**，而是等考试结束时间到点才解锁。
 * 否则先交卷的人可以把答案传给同一场次还没考的考生。
 */
@Data
@Builder
public class AttemptResultVO {

    private Long attemptId;

    private Long examId;

    private String examTitle;

    private String status;

    private LocalDateTime submitTime;

    private BigDecimal totalScore;

    private BigDecimal objectiveScore;

    private BigDecimal subjectiveScore;

    private BigDecimal passScore;

    private Integer isPass;

    /** 含主观题，成绩以教师复核为准 */
    private boolean needReview;

    /** 答案与逐题得分是否已解锁 */
    private boolean answerVisible;

    /** 解锁时间（考试结束时间），前端用于提示"xx 后可查看答案" */
    private LocalDateTime answerReleaseTime;

    private List<QuestionResult> questions;

    @Data
    @Builder
    public static class QuestionResult {
        private Long questionId;
        private Integer sortNo;
        private String type;
        private String typeLabel;
        private String stem;
        private List<Option> options;
        /** 我的作答 */
        private List<String> myAnswer;
        /** 正确答案；未解锁时为 null */
        private List<String> correctAnswer;
        /** 本题得分；未解锁时为 null（逐题对错同样属于答案信息） */
        private BigDecimal score;
        private BigDecimal fullScore;
        /** AUTO / MANUAL / UNJUDGED */
        private String judgeType;
        /** 未解锁时为 null */
        private Boolean fullMark;
    }

    @Data
    @Builder
    public static class Option {
        private String key;
        private String content;
    }
}
