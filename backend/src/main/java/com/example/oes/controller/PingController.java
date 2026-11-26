package com.example.oes.controller;

import com.example.oes.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行状态探针。用于部署后确认服务、JDK 版本、统一响应与链路追踪是否正常。
 */
@Tag(name = "00-系统")
@RestController
@RequestMapping("/api")
public class PingController {

    @Operation(summary = "健康探针")
    @GetMapping("/ping")
    public R<Map<String, Object>> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("java", System.getProperty("java.version"));
        body.put("app", "oes-backend");
        return R.ok(body);
    }
}
