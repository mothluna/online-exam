package com.example.oes.controller;

import com.example.oes.common.result.R;
import com.example.oes.dto.ReviewRequest;
import com.example.oes.security.RequireRole;
import com.example.oes.service.AnswerReviewService;
import com.example.oes.vo.AttemptDetailVO;
import com.example.oes.vo.ReviewResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "09-主观题复核")
@RestController
@RequestMapping("/api/admin")
@RequireRole("TEACHER")
@RequiredArgsConstructor
public class ReviewController {

    private final AnswerReviewService answerReviewService;

    @Operation(summary = "答卷详情（含答案与机器判分依据，教师只能看自己创建的考试）")
    @GetMapping("/attempts/{attemptId}/detail")
    public R<AttemptDetailVO> detail(@PathVariable Long attemptId) {
        return R.ok(answerReviewService.detail(attemptId));
    }

    @Operation(summary = "主观题复核打分（改分后自动重算总分与及格状态）")
    @PutMapping("/answers/{answerId}/review")
    public R<ReviewResultVO> review(@PathVariable Long answerId,
                                    @Valid @RequestBody ReviewRequest request) {
        return R.ok(answerReviewService.review(answerId, request));
    }
}
