package com.example.oes.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分数分布的一档。按满分百分比切 10 档，便于跨试卷比较。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreBucketVO {

    private String label;

    private long count;
}
