package com.example.oes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / springdoc 的文档元信息。
 *
 * <p>只加依赖不写这个类，`doc.html` 也能打开、接口也能列出来，但页面标题是默认的
 * "OpenAPI definition"、版本是 v0，看起来像没做完；更关键的是**没有声明 Bearer 鉴权方案时，
 * 文档页不会出现 Authorize 按钮**，所有需要令牌的接口只能手工往 Header 里贴 token。
 *
 * <p>这里刻意不配置全局 {@code security} 要求：登录、`/api/ping` 等是匿名接口，
 * 全局加锁会把它们也标成需要令牌，反而误导。鉴权方案声明一次即可，具体接口是否需要
 * 由权限矩阵决定，文档读者按需点 Authorize。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI oesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("在线考试系统 API")
                        .version("1.0.0")
                        .description("""
                                面向学校/培训机构的小型在线考试系统，覆盖「建题库 → 组卷 → 组织考试 → 作答 → 自动判卷 → 统计导出」闭环。

                                **鉴权方式**：先调 `POST /api/auth/login` 取 `token`，点右上角 Authorize 填入该 token（无需手动加 Bearer 前缀，方案类型已声明为 bearer）。

                                **角色**：`ADMIN`（超级角色，可见教师端全部菜单）、`TEACHER`、`STUDENT`。
                                接口的 401/403 语义严格区分：未登录或令牌失效返回 401，已登录但无权限返回 403。
                                """)
                        .contact(new Contact().name("OES 开发组")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("登录接口返回的 token，直接贴进来即可")));
    }
}
