package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.oes.common.constant.RedisKey;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;
import com.example.oes.dto.LoginRequest;
import com.example.oes.entity.SysUser;
import com.example.oes.mapper.SysUserMapper;
import com.example.oes.security.JwtService;
import com.example.oes.service.AuthService;
import com.example.oes.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 连续失败上限与锁定时长（FR-06-01） */
    private static final int MAX_FAIL = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    private static final Duration VERSION_CACHE = Duration.ofHours(24);

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;

    @Override
    public LoginVO login(LoginRequest request) {
        String failKey = RedisKey.loginFail(request.getUsername());
        String failCount = redis.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= MAX_FAIL) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "密码错误次数过多，请 10 分钟后再试");
        }

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFailure(failKey);
            // 不区分"用户不存在"与"密码错误"，避免账号探测
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号已被禁用，请联系管理员");
        }

        redis.delete(failKey);

        int authVersion = user.getAuthVersion() == null ? 0 : user.getAuthVersion();
        JwtService.IssuedToken issued = jwtService.issue(
                user.getId(), user.getUsername(), user.getRealName(), user.getRoleCode(), authVersion);

        // 白名单：存在即有效，删除即失效
        redis.opsForValue().set(RedisKey.token(issued.jti()),
                String.valueOf(user.getId()), Duration.ofSeconds(issued.expiresInSeconds()));
        redis.opsForValue().set(RedisKey.authVersion(user.getId()),
                String.valueOf(authVersion), VERSION_CACHE);

        SysUser touch = new SysUser();
        touch.setId(user.getId());
        touch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(touch);

        log.info("login ok: userId={}, role={}", user.getId(), user.getRoleCode());

        return LoginVO.builder()
                .token(issued.token())
                .tokenType("Bearer")
                .expiresIn(issued.expiresInSeconds())
                .user(LoginVO.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .role(user.getRoleCode())
                        .build())
                .build();
    }

    @Override
    public void logout(String jti) {
        redis.delete(RedisKey.token(jti));
    }

    @Override
    public int currentAuthVersion(Long userId) {
        String cached = redis.opsForValue().get(RedisKey.authVersion(userId));
        if (cached != null) {
            return Integer.parseInt(cached);
        }
        Integer fromDb = userMapper.selectAuthVersion(userId);
        if (fromDb == null) {
            // 用户已被删除，令牌自然失效
            throw new BizException(ResultCode.TOKEN_REVOKED);
        }
        redis.opsForValue().set(RedisKey.authVersion(userId), String.valueOf(fromDb), VERSION_CACHE);
        return fromDb;
    }

    @Override
    public int bumpAuthVersion(Long userId) {
        SysUser current = userMapper.selectById(userId);
        if (current == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        int next = (current.getAuthVersion() == null ? 0 : current.getAuthVersion()) + 1;

        SysUser update = new SysUser();
        update.setId(userId);
        update.setAuthVersion(next);
        userMapper.updateById(update);

        // 数据库与缓存必须同时更新，否则旧令牌还能在缓存 TTL 内继续使用
        redis.opsForValue().set(RedisKey.authVersion(userId), String.valueOf(next), VERSION_CACHE);
        log.info("auth version bumped: userId={}, ver={}", userId, next);
        return next;
    }

    private void recordFailure(String failKey) {        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redis.expire(failKey, LOCK_DURATION);
        }
    }
}
