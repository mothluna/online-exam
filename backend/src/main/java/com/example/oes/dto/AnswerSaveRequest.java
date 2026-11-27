package com.example.oes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量暂存。前端应整卷上报或按题增量上报，两种方式都幂等。
 */
@Data
public class AnswerSaveRequest {

    @NotNull(message = "答案列表不能为空")
    @Size(max = 500, message = "单次提交题目过多")
    @Valid
    private List<Item> items;

    /**
     * 作答会话号，取自 {@code GET /attempts/{id}}。
     * 允许为空：兼容未传会话的调用方（如集成测试），此时不做多标签页校验。
     */
    private String sessionId;

    @Data
    public static class Item {
        @NotNull(message = "题目 ID 不能为空")
        private Long questionId;

        /** 单选 ["A"]、多选 ["A","C"]、判断 ["正确"]、填空/简答按空或关键词 */
        private List<String> answer;
    }
}
