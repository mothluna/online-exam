package com.example.oes.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。只负责密码学部分，不碰 Redis 与数据库。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expire-minutes:120}") long expireMinutes) {
        // HS256 要求密钥不少于 256 bit，短于 32 字节会在启动时直接抛 WeakKeyException
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = expireMinutes * 60;
    }

    public IssuedToken issue(Long userId, String username, String realName, String role, int authVersion) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .claim("username", username)
                .claim("realName", realName)
                .claim("role", role)
                .claim("ver", authVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                // 显式指定 HS256：密钥超过 32 字节时 jjwt 会自动升到 HS384/HS512，
                // 与设计文档不一致，也让令牌长度无谓变长
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, jti, ttlSeconds);
    }

    /**
     * 解析并校验签名与有效期。失败抛 JwtException（含 ExpiredJwtException），由调用方翻译为 401。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public record IssuedToken(String token, String jti, long expiresInSeconds) {
    }
}
