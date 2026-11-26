package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaperQuestionVO {

    private Long questionId;

    private String type;

    private String typeLabel;

    private String stem;

    /** 本题分值（分） */
    private BigDecimal score;

    private Integer sortNo;
}
