package com.example.oes.controller;

import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.R;
import com.example.oes.dto.PaperQuery;
import com.example.oes.dto.PaperQuestionAddRequest;
import com.example.oes.dto.PaperSaveRequest;
import com.example.oes.security.RequireRole;
import com.example.oes.security.UserContext;
import com.example.oes.service.PaperService;
import com.example.oes.vo.PaperQuestionVO;
import com.example.oes.vo.PaperVO;
import com.example.oes.vo.RandomizeVO;
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

@Tag(name = "04-试卷")
@RestController
@RequestMapping("/api/admin/papers")
@RequireRole("TEACHER")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;

    @Operation(summary = "分页查询")
    @GetMapping
    public R<PageResult<PaperVO>> page(@Valid PaperQuery query) {
        return R.ok(paperService.page(query));
    }

    @Operation(summary = "创建试卷（规则合计须等于设定总分）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody PaperSaveRequest request) {
        return R.ok(paperService.create(request, UserContext.userId()));
    }

    @Operation(summary = "试卷详情")
    @GetMapping("/{id}")
    public R<PaperVO> detail(@PathVariable Long id) {
        return R.ok(paperService.detail(id));
    }

    @Operation(summary = "修改试卷（仅草稿）")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PaperSaveRequest request) {
        paperService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "按规则随机抽题（覆盖现有题目）")
    @PostMapping("/{id}/randomize")
    public R<RandomizeVO> randomize(@PathVariable Long id) {
        return R.ok(paperService.randomize(id));
    }

    @Operation(summary = "试卷题目列表")
    @GetMapping("/{id}/questions")
    public R<List<PaperQuestionVO>> questions(@PathVariable Long id) {
        return R.ok(paperService.questions(id));
    }

    @Operation(summary = "手工加题")
    @PostMapping("/{id}/questions")
    public R<Void> addQuestions(@PathVariable Long id, @Valid @RequestBody PaperQuestionAddRequest request) {
        paperService.addQuestions(id, request);
        return R.ok();
    }

    @Operation(summary = "移除题目")
    @DeleteMapping("/{id}/questions/{questionId}")
    public R<Void> removeQuestion(@PathVariable Long id, @PathVariable Long questionId) {
        paperService.removeQuestion(id, questionId);
        return R.ok();
    }

    @Operation(summary = "发布（总分事实校验）")
    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        paperService.publish(id);
        return R.ok();
    }

    @Operation(summary = "删除试卷（仅草稿）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        paperService.delete(id);
        return R.ok();
    }
}
