package com.example.oes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 主观题复核打分。
 *
 * <p>score 的单位是"分"（如 8.50），最多两位小数：
 * 后端内部用 0.01 分整数存储，这里用 movePointRight(2) 精确换算，
 * 不接受三位小数，避免"四舍五入吃掉 0.005"这类说不清的偏差。
 */
@Data
public class ReviewRequest {

    @NotNull(message = "得分不能为空")
    @DecimalMin(value = "0.00", message = "得分不能为负")
    private BigDecimal score;

    @Size(max = 200, message = "评语最多 200 字")
    private String comment;
}
