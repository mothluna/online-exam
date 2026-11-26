package com.example.oes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("paper_rule")
public class PaperRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private String type;

    private Integer questionCount;

    /** 单位 0.01 分 */
    private Integer scorePerItem;

    /** 逗号分隔的分类 ID；null 表示不限 */
    private String categoryIds;

    /** null 表示不限难度 */
    private Integer difficulty;

    private Integer sortNo;
}
