package com.example.oes.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量操作结果。
 *
 * <p>刻意不做"一个失败全批回滚"：批量停用里夹着"不能停用自己"或"最后一个管理员"，
 * 整批失败会让人不知道到底哪几个没生效。这里逐条执行、逐条回报，前端把跳过的原因列出来。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatusResultVO {

    private int success;

    private List<Failure> failures;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Failure {
        private Long userId;
        private String reason;
    }
}
