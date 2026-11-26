package com.example.oes.controller;

import com.example.oes.common.result.R;
import com.example.oes.dto.CategorySaveRequest;
import com.example.oes.security.RequireRole;
import com.example.oes.service.QuestionCategoryService;
import com.example.oes.vo.CategoryNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "02-题目分类")
@RestController
@RequestMapping("/api/admin/categories")
@RequireRole("TEACHER")
@RequiredArgsConstructor
public class QuestionCategoryController {

    private final QuestionCategoryService categoryService;

    @Operation(summary = "分类树（含题目数量）")
    @GetMapping
    public R<List<CategoryNode>> tree() {
        return R.ok(categoryService.tree());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public R<Long> create(@Valid @RequestBody CategorySaveRequest request) {
        return R.ok(categoryService.create(request));
    }

    @Operation(summary = "修改分类")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest request) {
        categoryService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除分类（有子分类或题目时拒绝）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
