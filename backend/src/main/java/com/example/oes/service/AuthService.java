package com.example.oes.service;

import com.example.oes.dto.LoginRequest;
import com.example.oes.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginRequest request);

    /** 注销：删除白名单条目，令牌立即失效 */
    void logout(String jti);

    /** 读取用户当前权限版本；缓存失效时回源数据库 */
    int currentAuthVersion(Long userId);

    /**
     * 递增权限版本，使该用户全部已签发令牌立即失效。
     * 改密、改角色、管理员强制下线必须调用此方法——
     * 只改数据库不删缓存会留下一段最长 24 小时的失效真空期。
     *
     * @return 递增后的版本号
     */
    int bumpAuthVersion(Long userId);
}
