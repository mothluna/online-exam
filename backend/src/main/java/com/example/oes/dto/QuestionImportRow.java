package com.example.oes.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 导入行。用 index 而不是 header 名匹配：
 * 用户调整列顺序、改标题文案都不会影响解析。
 */
@Data
@ColumnWidth(20)
public class QuestionImportRow {

    @ExcelProperty(value = "分类路径", index = 0)
    private String categoryPath;

    @ExcelProperty(value = "题型", index = 1)
    private String type;

    @ExcelProperty(value = "题干", index = 2)
    @ColumnWidth(50)
    private String stem;

    @ExcelProperty(value = "选项A", index = 3)
    private String optionA;

    @ExcelProperty(value = "选项B", index = 4)
    private String optionB;

    @ExcelProperty(value = "选项C", index = 5)
    private String optionC;

    @ExcelProperty(value = "选项D", index = 6)
    private String optionD;

    @ExcelProperty(value = "选项E", index = 7)
    private String optionE;

    @ExcelProperty(value = "选项F", index = 8)
    private String optionF;

    @ExcelProperty(value = "正确答案", index = 9)
    private String answer;

    @ExcelProperty(value = "解析", index = 10)
    @ColumnWidth(40)
    private String analysis;

    @ExcelProperty(value = "难度", index = 11)
    private String difficulty;

    @ExcelProperty(value = "分值", index = 12)
    private String score;
}
