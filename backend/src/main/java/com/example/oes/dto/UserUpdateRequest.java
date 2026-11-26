package com.example.oes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不超过 50 字")
    private String realName;

    /** 改角色会让该用户全部令牌立即失效 */
    @NotBlank(message = "角色不能为空")
    private String role;
}
