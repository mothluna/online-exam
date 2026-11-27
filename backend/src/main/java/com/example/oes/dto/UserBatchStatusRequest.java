package com.example.oes.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量启用/停用。
 *
 * <p>上限 200 是刻意的：这是给人点按钮用的，不是给脚本刷数据用的；
 * 再大就该走导入/后台任务，而不是在请求线程里逐个 @Transactional 提交。
 */
@Data
public class UserBatchStatusRequest {

    @NotEmpty(message = "请先选择用户")
    @Size(max = 200, message = "单次最多操作 200 个用户")
    private List<Long> userIds;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
