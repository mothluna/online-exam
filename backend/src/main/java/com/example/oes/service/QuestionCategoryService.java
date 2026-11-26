package com.example.oes.service;

import com.example.oes.dto.CategorySaveRequest;
import com.example.oes.vo.CategoryNode;

import java.util.List;

public interface QuestionCategoryService {

    /** 返回完整分类树，节点带题目数量 */
    List<CategoryNode> tree();

    Long create(CategorySaveRequest request);

    void update(Long id, CategorySaveRequest request);

    /** 含子分类或已挂题目的分类不可删除 */
    void delete(Long id);
}
