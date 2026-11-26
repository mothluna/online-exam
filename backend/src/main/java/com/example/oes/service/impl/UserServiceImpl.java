package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.ResultCode;
import com.example.oes.dto.UserCreateRequest;
import com.example.oes.dto.UserQuery;
import com.example.oes.dto.UserUpdateRequest;
import com.example.oes.entity.ExamAttempt;
import com.example.oes.entity.SysUser;
import com.example.oes.mapper.ExamAttemptMapper;
import com.example.oes.mapper.SysUserMapper;
import com.example.oes.security.UserContext;
import com.example.oes.service.AuthService;
import com.example.oes.service.UserService;
import com.example.oes.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> ROLES = Set.of("ADMIN", "TEACHER", "STUDENT");
    private static final String ADMIN = "ADMIN";

    private final SysUserMapper userMapper;
    private final ExamAttemptMapper attemptMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @Override
    public PageResult<UserVO> page(UserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .eq(query.getRole() != null && !query.getRole().isBlank(),
                        SysUser::getRoleCode, query.getRole() == null ? null : query.getRole().trim().toUpperCase())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        w -> w.like(SysUser::getUsername, query.getKeyword().trim())
                                .or()
                                .like(SysUser::getRealName, query.getKeyword().trim()))
                .orderByAsc(SysUser::getId);

        IPage<SysUser> page = userMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateRequest request) {
        String role = normalizeRole(request.getRole());

        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName().trim());
        user.setRoleCode(role);
        user.setStatus(1);
        user.setAuthVersion(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.ALREADY_STARTED, "用户名已存在");
        }
        log.info("user created: id={}, username={}, role={}", user.getId(), user.getUsername(), role);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserUpdateRequest request) {
        SysUser existing = load(id);
        String newRole = normalizeRole(request.getRole());
        boolean roleChanged = !newRole.equals(existing.getRoleCode());

        if (roleChanged) {
            if (id.equals(UserContext.userId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "不能修改自己的角色");
            }
            if (ADMIN.equals(existing.getRoleCode()) && countActiveAdmins() <= 1) {
                throw new BizException(ResultCode.PARAM_INVALID, "至少保留一个可用的管理员");
            }
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setRealName(request.getRealName().trim());
        update.setRoleCode(newRole);
        userMapper.updateById(update);

        if (roleChanged) {
            // 角色变了，旧令牌里的 role 声明就过期了，必须让全部令牌失效
            authService.bumpAuthVersion(id);
            log.info("user role changed: id={}, {} -> {}, tokens revoked", id, existing.getRoleCode(), newRole);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        load(id);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
        // 改密后所有已登录设备必须重新登录
        authService.bumpAuthVersion(id);
        log.info("password reset: id={}, tokens revoked", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.PARAM_INVALID, "状态只能是 0 或 1");
        }
        SysUser existing = load(id);
        if (id.equals(UserContext.userId()) && status == 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "不能停用自己的账号");
        }
        if (status == 0 && ADMIN.equals(existing.getRoleCode()) && countActiveAdmins() <= 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "至少保留一个可用的管理员");
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);

        if (status == 0) {
            // 停用即刻生效：否则已登录的人还能继续答题直到令牌过期
            authService.bumpAuthVersion(id);
            log.info("user disabled: id={}, tokens revoked", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kick(Long id) {
        load(id);
        authService.bumpAuthVersion(id);
        log.info("user kicked: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser existing = load(id);
        if (id.equals(UserContext.userId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "不能删除自己的账号");
        }
        long attempts = attemptMapper.selectCount(
                Wrappers.<ExamAttempt>lambdaQuery().eq(ExamAttempt::getUserId, id));
        if (attempts > 0) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "该用户有 " + attempts + " 份答卷，请改为停用以保留历史数据");
        }
        if (ADMIN.equals(existing.getRoleCode()) && countActiveAdmins() <= 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "至少保留一个可用的管理员");
        }
        userMapper.deleteById(id);
        // 不调 bumpAuthVersion：用户行已不存在，版本查询会直接抛 TOKEN_REVOKED，
        // 令牌自然失效；此时再去 update 不存在的用户只会把事务拖回滚
        log.info("user deleted: id={}, username={}", id, existing.getUsername());
    }

    // ---------------- 内部方法 ----------------

    private SysUser load(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private String normalizeRole(String raw) {
        String role = raw == null ? "" : raw.trim().toUpperCase();
        if (!ROLES.contains(role)) {
            throw new BizException(ResultCode.PARAM_INVALID, "角色只能是 ADMIN / TEACHER / STUDENT");
        }
        return role;
    }

    private long countActiveAdmins() {
        return userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleCode, ADMIN)
                .eq(SysUser::getStatus, 1));
    }

    private UserVO toVO(SysUser user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRoleCode())
                .roleLabel(roleLabel(user.getRoleCode()))
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String roleLabel(String role) {
        return switch (role) {
            case "ADMIN" -> "管理员";
            case "TEACHER" -> "教师";
            case "STUDENT" -> "学生";
            default -> role;
        };
    }
}
