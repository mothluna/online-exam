package com.example.oes.controller;

import com.example.oes.common.result.R;
import com.example.oes.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师端探针。用于验证角色鉴权：
 * 学生携带合法令牌访问应得 403，教师访问得 200。
 */
@Tag(name = "00-系统")
@RestController
@RequestMapping("/api/admin")
@RequireRole("TEACHER")
public class AdminPingController {

    @Operation(summary = "教师端探针（需 TEACHER 角色）")
    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("admin-ok");
    }
}
