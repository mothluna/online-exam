package com.example.oes.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色要求。标在 controller 类或方法上，由 AuthInterceptor 读取。
 * 不标即"登录即可访问"；ADMIN 视为超级角色，一律放行。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    String[] value();
}
