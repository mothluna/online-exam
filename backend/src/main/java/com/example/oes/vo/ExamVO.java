package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExamVO {

    private Long id;

    private Long paperId;

    private String paperTitle;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSec;

    private Integer allowRetake;

    private Integer shuffle;

    private String status;

    private BigDecimal totalScore;

    private Integer totalCount;

    /** 已开考人数，列表展示用 */
    private Long attemptCount;
}
