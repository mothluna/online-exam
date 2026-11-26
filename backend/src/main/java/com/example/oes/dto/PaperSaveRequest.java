package com.example.oes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PaperSaveRequest {

    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 200, message = "标题不超过 200 字")
    private String title;

    /** MANUAL / RANDOM，默认 RANDOM */
    private String composeType = "RANDOM";

    /** 设定总分（分）。RANDOM 必填，用于和规则合计对账；MANUAL 由题目汇总得出 */
    @DecimalMin(value = "0.01", message = "总分必须大于 0")
    @DecimalMax(value = "10000", message = "总分过大")
    private BigDecimal totalScore;

    @NotNull(message = "考试时长不能为空")
    @Min(value = 60, message = "时长至少 60 秒")
    @Max(value = 86400, message = "时长过长")
    private Integer durationSec;

    @DecimalMin(value = "0", message = "及格分不能为负")
    private BigDecimal passScore = BigDecimal.ZERO;

    /** 抽题规则，RANDOM 必填且不能为空 */
    @Valid
    private List<RuleItem> rules;

    @Data
    public static class RuleItem {
        @NotBlank(message = "题型不能为空")
        private String type;

        @NotNull(message = "抽题数量不能为空")
        @Min(value = 1, message = "抽题数量至少 1")
        @Max(value = 500, message = "单题型抽题数量过大")
        private Integer questionCount;

        @NotNull(message = "每题分值不能为空")
        @DecimalMin(value = "0.01", message = "分值必须大于 0")
        @DecimalMax(value = "100", message = "单题分值不超过 100")
        private BigDecimal scorePerItem;

        /** 限定分类；空表示不限 */
        private List<Long> categoryIds;

        /** 限定难度；null 表示不限 */
        @Min(value = 1, message = "难度取 1~3")
        @Max(value = 3, message = "难度取 1~3")
        private Integer difficulty;
    }
}
