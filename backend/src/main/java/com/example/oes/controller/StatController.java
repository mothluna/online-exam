package com.example.oes.controller;

import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.R;
import com.example.oes.dto.ExamQuery;
import com.example.oes.security.RequireRole;
import com.example.oes.service.ExportService;
import com.example.oes.service.StatService;
import com.example.oes.vo.AttemptStatVO;
import com.example.oes.vo.ExamStatVO;
import com.example.oes.vo.ExportTaskVO;
import com.example.oes.vo.ScoreBucketVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "07-记录统计")
@RestController
@RequestMapping("/api/admin/stats")
@RequireRole("TEACHER")
@RequiredArgsConstructor
public class StatController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final StatService statService;
    private final ExportService exportService;

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

    @Operation(summary = "分数分布（mode=percent 十等分 / absolute 每 5 分一档）")
    @GetMapping("/exams/{examId}/distribution")
    public R<List<ScoreBucketVO>> distribution(@PathVariable Long examId,
                                               @RequestParam(defaultValue = "percent") String mode) {
        return R.ok(statService.scoreDistribution(examId, mode));
    }

    /**
     * 导出成绩单。行数 ≤ 5000 直接流式返回文件（200）；
     * 超出则返回 202 + taskId，前端轮询 /export-tasks/{taskId} 取下载链接。
     */
    @Operation(summary = "导出成绩单（≤5000 行直接下载，超出转异步）")
    @GetMapping("/exams/{examId}/export")
    public ResponseEntity<?> export(@PathVariable Long examId, HttpServletResponse response) throws IOException {
        ExportService.ExportPlan plan = exportService.plan(examId);

        if (!plan.direct()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", plan.taskId());
            payload.put("rowCount", plan.total());
            payload.put("status", "PENDING");
            return ResponseEntity.status(202).body(R.ok(payload));
        }

        response.setContentType(XLSX);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(plan.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString());
        exportService.stream(examId, response.getOutputStream());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "查询导出任务状态")
    @GetMapping("/export-tasks/{taskId}")
    public R<ExportTaskVO> exportTask(@PathVariable String taskId) {
        return R.ok(exportService.task(taskId));
    }
}
