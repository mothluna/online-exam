package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PaperVO {

    private Long id;

    private String title;

    private String composeType;

    private BigDecimal totalScore;

    private Integer totalCount;

    private BigDecimal passScore;

    private Integer durationSec;

    private String status;

    private LocalDateTime createdAt;

    private List<RuleVO> rules;

    /** 实际已加入试卷的题目数（DRAFT 阶段可能与 totalCount 暂不相等） */
    private Long actualCount;

    private BigDecimal actualScore;

    @Data
    @Builder
    public static class RuleVO {
        private String type;
        private String typeLabel;
        private Integer questionCount;
        private BigDecimal scorePerItem;
        private List<Long> categoryIds;
        private Integer difficulty;
    }
}
