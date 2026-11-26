package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RandomizeVO {

    private Integer totalCount;

    private BigDecimal totalScore;

    /** 耗时，用于观察抽题性能 */
    private long elapsedMs;
}
