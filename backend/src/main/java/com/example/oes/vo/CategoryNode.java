package com.example.oes.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryNode {

    private Long id;

    private Long parentId;

    private String name;

    private Integer sortNo;

    private Integer status;

    /** 该分类下的题目数量 */
    private long questionCount;

    private List<CategoryNode> children = new ArrayList<>();

    public void addChild(CategoryNode child) {
        this.children.add(child);
    }
}
