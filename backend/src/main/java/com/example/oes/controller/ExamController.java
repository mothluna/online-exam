package com.example.oes.controller;

import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.R;
import com.example.oes.dto.ExamQuery;
import com.example.oes.dto.ExamSaveRequest;
import com.example.oes.security.RequireRole;
import com.example.oes.security.UserContext;
import com.example.oes.service.ExamService;
import com.example.oes.vo.ExamVO;
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

@Tag(name = "05-考试管理")
@RestController
@RequestMapping("/api/admin/exams")
@RequireRole("TEACHER")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @Operation(summary = "分页查询")
    @GetMapping
    public R<PageResult<ExamVO>> page(@Valid ExamQuery query) {
        return R.ok(examService.page(query));
    }

    @Operation(summary = "创建考试（绑定已发布试卷）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ExamSaveRequest request) {
        return R.ok(examService.create(request, UserContext.userId()));
    }

    @Operation(summary = "考试详情")
    @GetMapping("/{id}")
    public R<ExamVO> detail(@PathVariable Long id) {
        return R.ok(examService.detail(id));
    }

    @Operation(summary = "修改考试（仅草稿）")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ExamSaveRequest request) {
        examService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "发布考试")
    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        examService.publish(id);
        return R.ok();
    }

    @Operation(summary = "删除考试（仅草稿）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        examService.delete(id);
        return R.ok();
    }
}
