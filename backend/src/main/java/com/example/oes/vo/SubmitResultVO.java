package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SubmitResultVO {

    private Long attemptId;

    private String status;

    private LocalDateTime submitTime;

    private BigDecimal totalScore;

    private Integer isPass;

    /** 主观题是否需要教师复核（自动判卷模块接入后置位） */
    private Boolean needReview;
}
