package com.example.oes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_attempt")
public class ExamAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long userId;

    private Long paperId;

    private Integer seqNo;

    /** IN_PROGRESS / SUBMITTED / TIMEOUT / GRADED */
    private String status;

    private LocalDateTime startTime;

    private LocalDateTime deadline;

    private LocalDateTime submitTime;

    private Integer objectiveScore;

    private Integer subjectiveScore;

    private Integer totalScore;

    private Integer isPass;

    private Integer version;

    private LocalDateTime createdAt;
}
