package com.example.oes.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 导出用的行结构。既是 EasyExcel 的写出模型，也是 MyBatis 的结果类型，
 * 所以只能用 @Data 生成无参构造 + setter，不能加 @Builder。
 */
@Data
@ColumnWidth(18)
public class AttemptExportRow {

    @ExcelProperty(value = "答卷ID", index = 0)
    @ColumnWidth(10)
    private Long attemptId;

    @ExcelProperty(value = "用户名", index = 1)
    private String username;

    @ExcelProperty(value = "姓名", index = 2)
    /** 不能用 userName：与 username 在 MyBatis 列名匹配中大小写不敏感，会互相覆盖 */
    private String realName;

    @ExcelProperty(value = "状态", index = 3)
    @ColumnWidth(12)
    private String statusText;

    @ExcelProperty(value = "得分", index = 4)
    @ColumnWidth(10)
    private BigDecimal totalScore;

    @ExcelProperty(value = "是否及格", index = 5)
    @ColumnWidth(10)
    private String passText;

    @ExcelProperty(value = "提交时间", index = 6)
    @ColumnWidth(22)
    private LocalDateTime submitTime;

    @ExcelProperty(value = "用时(秒)", index = 7)
    @ColumnWidth(10)
    private Long durationSec;
}
