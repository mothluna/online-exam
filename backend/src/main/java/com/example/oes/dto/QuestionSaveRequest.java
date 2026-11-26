package com.example.oes.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class QuestionSaveRequest {

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /** SINGLE / MULTIPLE / JUDGE / BLANK / SHORT，也接受中文名 */
    @NotBlank(message = "题型不能为空")
    private String type;

    @NotBlank(message = "题干不能为空")
    @Size(max = 5000, message = "题干过长")
    private String stem;

    /** 选择题必填；判断/填空/简答传空 */
    private List<Option> options;

    /** 单选一项、多选多项；判断题填"正确"/"错误"；填空/简答填参考答案或关键词 */
    @NotEmpty(message = "答案不能为空")
    private List<String> answer;

    private String analysis;

    @Min(value = 1, message = "难度取 1~3")
    @Max(value = 3, message = "难度取 1~3")
    private Integer difficulty = 2;

    /** 单位：分，最多两位小数 */
    @NotNull(message = "分值不能为空")
    @DecimalMin(value = "0.01", message = "分值必须大于 0")
    @DecimalMax(value = "100", message = "单题分值不超过 100")
    private BigDecimal defaultScore;

    private Integer status = 1;

    @Data
    public static class Option {
        private String key;
        private String content;
    }
}
