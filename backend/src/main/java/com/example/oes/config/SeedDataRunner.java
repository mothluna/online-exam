package com.example.oes.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.oes.entity.SysUser;
import com.example.oes.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 演示账号初始化。仅在用户表为空时执行一次，避免每次启动重复插入。
 * 生产环境通过 app.seed.enabled=false 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SeedDataRunner implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery());
        if (count != null && count > 0) {
            return;
        }
        create("admin", "系统管理员", "ADMIN");
        create("teacher", "张老师", "TEACHER");
        create("student", "李同学", "STUDENT");
        log.info("seed users created, default password = {}", DEFAULT_PASSWORD);
    }

    private void create(String username, String realName, String role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRealName(realName);
        user.setRoleCode(role);
        user.setStatus(1);
        user.setAuthVersion(0);
        userMapper.insert(user);
    }
}
