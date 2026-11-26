package com.example.oes.controller;

import com.example.oes.common.result.R;
import com.example.oes.dto.AnswerSaveRequest;
import com.example.oes.security.RequireRole;
import com.example.oes.security.UserContext;
import com.example.oes.service.ExamTakingService;
import com.example.oes.vo.AttemptResultVO;
import com.example.oes.vo.AttemptVO;
import com.example.oes.vo.StudentExamVO;
import com.example.oes.vo.SubmitResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "06-考试作答")
@RestController
@RequestMapping("/api/student")
@RequireRole("STUDENT")
@RequiredArgsConstructor
public class StudentExamController {

    private final ExamTakingService examTakingService;

    @Operation(summary = "可参加的考试（含本人状态）")
    @GetMapping("/exams")
    public R<List<StudentExamVO>> list() {
        return R.ok(examTakingService.listForStudent(UserContext.userId()));
    }

    @Operation(summary = "开始考试（重复开考返回 409 + 已有答卷 ID）")
    @PostMapping("/exams/{examId}/start")
    public R<AttemptVO> start(@PathVariable Long examId) {
        return R.ok(examTakingService.start(examId, UserContext.userId()));
    }

    @Operation(summary = "续考 / 查看答卷（不含答案）")
    @GetMapping("/attempts/{attemptId}")
    public R<AttemptVO> attempt(@PathVariable Long attemptId) {
        return R.ok(examTakingService.getAttempt(attemptId, UserContext.userId()));
    }

    @Operation(summary = "批量暂存答案（幂等）")
    @PutMapping("/attempts/{attemptId}/answers")
    public R<Void> saveAnswers(@PathVariable Long attemptId,
                               @Valid @RequestBody AnswerSaveRequest request) {
        examTakingService.saveAnswers(attemptId, UserContext.userId(), request);
        return R.ok();
    }

    @Operation(summary = "成绩单（考试结束前不返回答案）")
    @GetMapping("/attempts/{attemptId}/result")
    public R<AttemptResultVO> result(@PathVariable Long attemptId) {
        return R.ok(examTakingService.result(attemptId, UserContext.userId()));
    }

    @Operation(summary = "交卷（幂等，超时提交标记 TIMEOUT）")
    @PostMapping("/attempts/{attemptId}/submit")
    public R<SubmitResultVO> submit(@PathVariable Long attemptId) {
        return R.ok(examTakingService.submit(attemptId, UserContext.userId()));
    }
}
