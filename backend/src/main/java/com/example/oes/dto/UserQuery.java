package com.example.oes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private long page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private long size = 20;

    /** ADMIN / TEACHER / STUDENT */
    private String role;

    private Integer status;

    /** 用户名或姓名关键字 */
    private String keyword;
}
