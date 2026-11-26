package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String token;

    private String tokenType;

    private long expiresIn;

    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String realName;
        private String role;
    }
}
