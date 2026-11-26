package com.example.oes.security;

import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;

/**
 * 请求级用户上下文。必须由拦截器在 afterCompletion 中清理，
 * 否则线程池复用时会把上一个请求的身份带给下一个请求。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 需要登录的场景直接用这个取，取不到即视为未认证 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long userId() {
        return require().userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
