package com.example.oes.security;

/**
 * 当前请求的登录用户。只放鉴权必需的信息，不放实体，避免误用。
 */
public record LoginUser(Long userId, String username, String realName, String role, String jti) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
