package com.example.oes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attempt_answer")
public class AttemptAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long attemptId;

    private Long questionId;

    private String answerJson;

    private Integer score;

    private String judgeType;

    private String judgeDetail;

    private Integer sortNo;

    private LocalDateTime updatedAt;
}
