package com.example.oes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oes.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 只做数据访问，不承载任何业务判断。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT auth_version FROM sys_user WHERE id = #{id}")
    Integer selectAuthVersion(@Param("id") Long id);
}
