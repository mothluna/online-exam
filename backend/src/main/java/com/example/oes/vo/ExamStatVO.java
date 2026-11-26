package com.example.oes.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统计列表行。作为 MyBatis 结果类型，必须有**无参构造**：
 * Lombok 的 @Builder 会只留下全参构造，MyBatis 会误以为要走构造器自动映射，
 * 字段顺序对不上就抛 IndexOutOfBoundsException。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamStatVO {

    private Long examId;

    private String title;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal paperTotalScore;

    /** 开考人数（含未提交） */
    private long joinCount;

    /** 已提交人数（含超时自动交卷） */
    private long submitCount;

    private BigDecimal avgScore;

    private BigDecimal maxScore;

    private BigDecimal minScore;

    private long passCount;

    /** 及格率，百分比，保留一位小数 */
    private BigDecimal passRate;
}
