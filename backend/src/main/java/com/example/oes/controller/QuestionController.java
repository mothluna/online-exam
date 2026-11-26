package com.example.oes.controller;

import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.R;
import com.example.oes.dto.QuestionQuery;
import com.example.oes.dto.QuestionSaveRequest;
import com.example.oes.security.RequireRole;
import com.example.oes.security.UserContext;
import com.example.oes.service.QuestionImportService;
import com.example.oes.service.QuestionService;
import com.example.oes.vo.ImportResultVO;
import com.example.oes.vo.QuestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Tag(name = "03-题目")
@RestController
@RequestMapping("/api/admin/questions")
@RequireRole("TEACHER")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionImportService importService;

    @Operation(summary = "分页查询（不含答案与解析）")
    @GetMapping
    public R<PageResult<QuestionVO>> page(@Valid QuestionQuery query) {
        return R.ok(questionService.page(query));
    }

    @Operation(summary = "题目详情（含答案）")
    @GetMapping("/{id}")
    public R<QuestionVO> detail(@PathVariable Long id) {
        return R.ok(questionService.detail(id));
    }

    @Operation(summary = "新增题目（题干重复返回 409）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody QuestionSaveRequest request) {
        return R.ok(questionService.create(request, UserContext.userId()));
    }

    @Operation(summary = "修改题目")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody QuestionSaveRequest request) {
        questionService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return R.ok();
    }

    @Operation(summary = "启用/停用")
    @PatchMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        questionService.changeStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "Excel 批量导入")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportResultVO> importQuestions(@RequestPart("file") MultipartFile file) {
        return R.ok(importService.importQuestions(file, UserContext.userId()));
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] bytes = importService.template();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("question-import-template.xlsx", StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
