package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class StudentExamVO {

    private Long id;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSec;

    private BigDecimal totalScore;

    private Integer totalCount;

    /** NOT_STARTED / IN_PROGRESS / SUBMITTED / ENDED */
    private String state;

    /** 已有答卷时回带，前端可直接续考 */
    private Long attemptId;

    /** 本人得分（0.01 分为单位的整数在前端换算）；未交卷时为 null */
    private BigDecimal score;

    /** 是否及格；未交卷时为 null */
    private Integer isPass;
}
