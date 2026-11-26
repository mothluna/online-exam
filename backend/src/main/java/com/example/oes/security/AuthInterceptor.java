package com.example.oes.security;

import com.example.oes.common.constant.RedisKey;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;
import com.example.oes.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 认证与功能级鉴权。校验顺序即语义，不能调换：
 * 验签 → 白名单 → 版本号 → 角色。
 * 前三步失败一律 401（未认证/令牌失效），第四步失败才是 403（越权）。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final AuthService authService;
    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }

        String jti = claims.getId();
        if (jti == null || redis.opsForValue().get(RedisKey.token(jti)) == null) {
            throw new BizException(ResultCode.TOKEN_REVOKED);
        }

        Long userId = Long.valueOf(claims.getSubject());
        Integer tokenVersion = claims.get("ver", Integer.class);
        if (tokenVersion == null || tokenVersion != authService.currentAuthVersion(userId)) {
            // 改密 / 改角色 / 强制下线后，旧令牌在此处一次全部失效
            throw new BizException(ResultCode.TOKEN_REVOKED);
        }

        String role = claims.get("role", String.class);
        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required != null && !contains(required.value(), role)) {
            throw new BizException(ResultCode.FORBIDDEN_ROLE);
        }

        UserContext.set(new LoginUser(
                userId,
                claims.get("username", String.class),
                claims.get("realName", String.class),
                role,
                jti));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean contains(String[] allowed, String role) {
        // ADMIN 是超级角色，不在注解里逐个声明也放行
        return "ADMIN".equals(role) || Arrays.asList(allowed).contains(role);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
