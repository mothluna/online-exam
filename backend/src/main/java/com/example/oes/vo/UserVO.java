package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String role;

    private String roleLabel;

    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
