package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ExportTaskVO {

    private String taskId;

    /** PENDING / RUNNING / DONE / FAILED */
    private String status;

    private long rowCount;

    /** DONE 时为预签名下载地址 */
    private String url;

    private String message;

    private LocalDateTime createdAt;
}
