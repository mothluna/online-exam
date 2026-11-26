package com.example.oes.controller;

import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.R;
import com.example.oes.dto.ExamQuery;
import com.example.oes.security.RequireRole;
import com.example.oes.service.StatService;
import com.example.oes.vo.AttemptStatVO;
import com.example.oes.vo.ExamStatVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "07-记录统计")
@RestController
@RequestMapping("/api/admin/stats")
@RequireRole("TEACHER")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @Operation(summary = "按考试聚合分页（参考人数/平均分/及格率）")
    @GetMapping("/exams")
    public R<PageResult<ExamStatVO>> examStats(@Valid ExamQuery query) {
        return R.ok(statService.pageExamStats(query));
    }

    @Operation(summary = "某场考试的成绩明细")
    @GetMapping("/exams/{examId}/attempts")
    public R<PageResult<AttemptStatVO>> attempts(@PathVariable Long examId,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return R.ok(statService.pageAttempts(examId, page, size));
    }
}
