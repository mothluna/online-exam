package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考生视角的答卷。题目结构与分值在这里下发，
 * **绝不包含正确答案与解析**——否则前端 F12 即可看答案。
 */
@Data
@Builder
public class AttemptVO {

    private Long attemptId;

    private Long examId;

    private String examTitle;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime deadline;

    /** 服务端当前时间，前端用它校准倒计时，避免本机时间被改 */
    private LocalDateTime serverTime;

    private BigDecimal totalScore;

    private Integer totalCount;

    private List<QuestionVO> questions;

    private List<AnswerVO> answers;

    /** 本次作答会话号；多标签页只保留最后一次加载的那一个 */
    private String sessionId;

    @Data
    @Builder
    public static class QuestionVO {
        private Long questionId;
        private String type;
        private String typeLabel;
        private String stem;
        private List<Option> options;
        private BigDecimal score;
        private Integer sortNo;
        /** 填空题空数；前端据此渲染输入框，不再数题干下划线 */
        private Integer blankCount;
    }

    @Data
    @Builder
    public static class Option {
        private String key;
        private String content;
    }

    @Data
    @Builder
    public static class AnswerVO {
        private Long questionId;
        private List<String> answer;
    }
}
