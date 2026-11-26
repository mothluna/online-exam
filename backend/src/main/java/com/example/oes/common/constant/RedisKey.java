package com.example.oes.common.constant;

/**
 * Redis 键空间。集中定义避免各处手拼字符串导致拼写漂移。
 */
public final class RedisKey {

    private RedisKey() {
    }

    /** 令牌白名单：key 存 userId，删除即失效 */
    private static final String TOKEN = "oes:token:%s";

    /** 用户权限版本，令牌内 ver 与之不符即失效 */
    private static final String AUTH_VERSION = "oes:authver:%s";

    /** 登录失败计数，用于锁定 */
    private static final String LOGIN_FAIL = "oes:login:fail:%s";

    public static String token(String jti) {
        return TOKEN.formatted(jti);
    }

    public static String authVersion(Long userId) {
        return AUTH_VERSION.formatted(userId);
    }

    public static String loginFail(String username) {
        return LOGIN_FAIL.formatted(username);
    }

    /** 开考并发前置拦截，10 秒自动过期 */
    private static final String EXAM_START_LOCK = "oes:start:%s:%s";

    /** 答卷截止时间快查，避免每次暂存/提交都读库 */
    private static final String ATTEMPT_DEADLINE = "oes:deadline:%s";

    public static String examStartLock(Long examId, Long userId) {
        return EXAM_START_LOCK.formatted(examId, userId);
    }

    public static String attemptDeadline(Long attemptId) {
        return ATTEMPT_DEADLINE.formatted(attemptId);
    }
}
