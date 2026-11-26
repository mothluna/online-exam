package com.example.oes.common.enums;

import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;

/**
 * 题型。中文名与代码的唯一映射点，Excel 导入、接口入参都从这里解析。
 */
public enum QuestionType {

    SINGLE("单选"),
    MULTIPLE("多选"),
    JUDGE("判断"),
    BLANK("填空"),
    SHORT("简答");

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static QuestionType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "题型不能为空");
        }
        String v = raw.trim();
        for (QuestionType t : values()) {
            if (t.name().equalsIgnoreCase(v) || t.label.equals(v)) {
                return t;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID, "未知题型: " + raw);
    }
}
