package com.example.oes.controller;

import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.R;
import com.example.oes.dto.PasswordResetRequest;
import com.example.oes.dto.UserCreateRequest;
import com.example.oes.dto.UserQuery;
import com.example.oes.dto.UserUpdateRequest;
import com.example.oes.security.RequireRole;
import com.example.oes.service.UserService;
import com.example.oes.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "08-用户管理")
@RestController
@RequestMapping("/api/admin/users")
@RequireRole("ADMIN")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户分页查询")
    @GetMapping
    public R<PageResult<UserVO>> page(@Valid UserQuery query) {
        return R.ok(userService.page(query));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public R<Long> create(@Valid @RequestBody UserCreateRequest request) {
        return R.ok(userService.create(request));
    }

    @Operation(summary = "修改姓名与角色（改角色会使该用户令牌立即失效）")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "重置密码（同时踢下线）")
    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return R.ok();
    }

    @Operation(summary = "启用/停用（停用即踢下线）")
    @PatchMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.changeStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "强制下线")
    @PostMapping("/{id}/kick")
    public R<Void> kick(@PathVariable Long id) {
        userService.kick(id);
        return R.ok();
    }

    @Operation(summary = "删除用户（有答卷记录时拒绝）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}
