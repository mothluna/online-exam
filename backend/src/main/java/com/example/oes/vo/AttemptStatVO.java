package com.example.oes.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩明细行。直接由 SQL 映射（read model），不再经过实体中转。
 */
@Data
public class AttemptStatVO {

    private Long attemptId;

    private Long userId;

    private String realName;

    private String status;

    private BigDecimal totalScore;

    private Integer isPass;

    private LocalDateTime submitTime;

    /** 作答时长（秒）：提交时间 - 开始时间，未提交按截止时间算 */
    private Long durationSec;
}
