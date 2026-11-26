package com.example.oes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class QuestionQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private long page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private long size = 20;

    private Long categoryId;

    private String type;

    private Integer difficulty;

    private Integer status;

    /** 题干关键字，模糊匹配 */
    private String keyword;
}
