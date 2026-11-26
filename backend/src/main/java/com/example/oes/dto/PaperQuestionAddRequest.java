package com.example.oes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 手工组卷：一次提交若干题目，分值可选（缺省取题库默认分）。
 */
@Data
public class PaperQuestionAddRequest {

    @NotEmpty(message = "至少选择一道题目")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        private Long questionId;

        /** 分；不传则使用题库默认分值 */
        private BigDecimal score;
    }
}
