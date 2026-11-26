package com.example.oes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    /** SINGLE / MULTIPLE / JUDGE / BLANK / SHORT */
    private String type;

    private String stem;

    /** 归一化题干的 SHA-256，唯一索引 */
    private String stemHash;

    /** JSON 字符串：[{"key":"A","content":"..."}] */
    private String optionsJson;

    /** JSON 字符串：["A"] / ["A","C"] / ["正确"] */
    private String answerJson;

    private String analysis;

    /** 1 易，2 中，3 难 */
    private Integer difficulty;

    /** 单位 0.01 分 */
    private Integer defaultScore;

    private Integer status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
