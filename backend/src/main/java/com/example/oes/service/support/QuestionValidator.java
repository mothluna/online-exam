package com.example.oes.service.support;

import com.example.oes.common.enums.QuestionType;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.model.OptionItem;
import com.example.oes.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 题目规则校验。单条新增与 Excel 导入共用同一套规则，
 * 否则会出现"接口能存、导入却报错"或相反的不一致。
 */
@Component
public class QuestionValidator {

    private static final Set<String> JUDGE_ANSWERS = Set.of("正确", "错误");

    /** 去空、去首尾空白，保持原有顺序 */
    public List<String> normalizeAnswer(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(raw.size());
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String v = s.trim();
            if (!v.isEmpty()) {
                result.add(v);
            }
        }
        return result;
    }

    /**
     * @throws BizException 校验不通过时抛出，message 可直接展示给用户
     */
    public void validate(QuestionType type, List<OptionItem> options, List<String> answer) {
        if (answer == null || answer.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "答案不能为空");
        }
        switch (type) {
            case SINGLE, MULTIPLE -> {
                if (options == null || options.size() < 2) {
                    throw new BizException(ResultCode.PARAM_INVALID, "选择题至少需要 2 个选项");
                }
                Set<String> keys = new HashSet<>();
                for (OptionItem option : options) {
                    if (option.key() == null || option.key().isBlank()
                            || option.content() == null || option.content().isBlank()) {
                        throw new BizException(ResultCode.PARAM_INVALID, "选项标识与内容不能为空");
                    }
                    if (!keys.add(option.key().trim())) {
                        throw new BizException(ResultCode.PARAM_INVALID, "选项标识重复: " + option.key());
                    }
                }
                if (type == QuestionType.SINGLE && answer.size() != 1) {
                    throw new BizException(ResultCode.PARAM_INVALID, "单选题只能有一个正确答案");
                }
                if (type == QuestionType.MULTIPLE && answer.size() < 2) {
                    throw new BizException(ResultCode.PARAM_INVALID, "多选题至少需要两个正确答案");
                }
                for (String a : answer) {
                    if (!keys.contains(a)) {
                        throw new BizException(ResultCode.PARAM_INVALID, "答案不在选项中: " + a);
                    }
                }
            }
            case JUDGE -> {
                if (answer.size() != 1 || !JUDGE_ANSWERS.contains(answer.get(0))) {
                    throw new BizException(ResultCode.PARAM_INVALID, "判断题答案只能是「正确」或「错误」");
                }
            }
            case BLANK, SHORT -> {
                if (options != null && !options.isEmpty()) {
                    throw new BizException(ResultCode.PARAM_INVALID, "填空题与简答题不应包含选项");
                }
            }
            default -> throw new BizException(ResultCode.PARAM_INVALID, "不支持的题型");
        }
    }

    public List<OptionItem> toOptionItems(List<String> keys, List<String> contents) {
        List<OptionItem> items = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String content = i < contents.size() ? contents.get(i) : null;
            if (content == null || content.isBlank()) {
                continue;
            }
            items.add(new OptionItem(keys.get(i), content.trim()));
        }
        return items;
    }

    public boolean isBlank(String s) {
        return Objects.isNull(s) || s.isBlank();
    }
}
