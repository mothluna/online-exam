package com.example.oes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategorySaveRequest {

    /** 不传或 0 表示根节点 */
    private Long parentId = 0L;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不超过 50 字")
    private String name;

    private Integer sortNo = 0;

    private Integer status = 1;
}
