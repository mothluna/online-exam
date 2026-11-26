package com.example.oes.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 失败明细行。回写给用户看，行列要对得上，否则用户无法定位。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ColumnWidth(20)
public class ImportErrorRow {

    @ExcelProperty(value = "行号", index = 0)
    private Integer rowNo;

    @ExcelProperty(value = "题干", index = 1)
    @ColumnWidth(50)
    private String stem;

    @ExcelProperty(value = "失败原因", index = 2)
    @ColumnWidth(40)
    private String reason;
}
