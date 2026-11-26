package com.example.oes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 单场时长（秒） */
    private Integer durationSec;

    private Integer allowRetake;

    private Integer shuffle;

    /** DRAFT / PUBLISHED / CLOSED */
    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
