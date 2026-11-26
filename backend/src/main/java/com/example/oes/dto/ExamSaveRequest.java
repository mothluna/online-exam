package com.example.oes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExamSaveRequest {

    @NotNull(message = "试卷不能为空")
    private Long paperId;

    @NotBlank(message = "考试名称不能为空")
    @Size(max = 200, message = "名称不超过 200 字")
    private String title;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "考试时长不能为空")
    @Min(value = 60, message = "时长至少 60 秒")
    @Max(value = 86400, message = "时长过长")
    private Integer durationSec;

    private Integer allowRetake = 0;

    private Integer shuffle = 0;
}
