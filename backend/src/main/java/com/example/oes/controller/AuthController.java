package com.example.oes.controller;

import com.example.oes.common.result.R;
import com.example.oes.dto.LoginRequest;
import com.example.oes.security.UserContext;
import com.example.oes.service.AuthService;
import com.example.oes.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @Operation(summary = "注销（令牌立即失效）")
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout(UserContext.require().jti());
        return R.ok();
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public R<LoginVO.UserInfo> me() {
        var user = UserContext.require();
        return R.ok(LoginVO.UserInfo.builder()
                .id(user.userId())
                .username(user.username())
                .realName(user.realName())
                .role(user.role())
                .build());
    }
}
