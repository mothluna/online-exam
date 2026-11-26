package com.example.oes.service;

import com.example.oes.common.result.PageResult;
import com.example.oes.dto.UserCreateRequest;
import com.example.oes.dto.UserQuery;
import com.example.oes.dto.UserUpdateRequest;
import com.example.oes.vo.UserVO;

public interface UserService {

    PageResult<UserVO> page(UserQuery query);

    Long create(UserCreateRequest request);

    /** 姓名与角色；角色变化时递增权限版本，旧令牌立即失效 */
    void update(Long id, UserUpdateRequest request);

    /** 重置密码，同时递增权限版本 */
    void resetPassword(Long id, String newPassword);

    /** 启用/停用；停用会踢掉该用户所有会话 */
    void changeStatus(Long id, Integer status);

    /** 强制下线：递增权限版本，不删除账号 */
    void kick(Long id);

    void delete(Long id);
}
