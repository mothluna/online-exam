package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** 复核打分后的最新汇总，前端用它就地更新成绩明细行 */
@Data
@Builder
public class ReviewResultVO {

    private Long attemptId;

    private BigDecimal subjectiveScore;

    private BigDecimal totalScore;

    private Integer isPass;

    /** 还有几道主观题没复核 */
    private int pendingReviewCount;

    private boolean needReview;
}
