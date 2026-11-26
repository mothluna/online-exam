package com.example.oes.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String type;

    private String typeLabel;

    private String stem;

    private List<Option> options;

    private List<String> answer;

    private String analysis;

    private Integer difficulty;

    /** 分值，单位：分 */
    private BigDecimal score;

    private Integer status;

    private LocalDateTime createdAt;

    @Data
    public static class Option {
        private String key;
        private String content;
    }
}
