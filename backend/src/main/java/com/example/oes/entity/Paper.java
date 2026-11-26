package com.example.oes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper")
public class Paper {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** MANUAL / RANDOM */
    private String composeType;

    /** 单位 0.01 分 */
    private Integer totalScore;

    private Integer totalCount;

    private Integer passScore;

    private Integer durationSec;

    /** DRAFT / PUBLISHED */
    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
