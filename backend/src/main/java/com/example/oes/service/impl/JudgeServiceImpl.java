package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.oes.common.enums.QuestionType;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;
import com.example.oes.common.util.ScoreRules;
import com.example.oes.entity.AttemptAnswer;
import com.example.oes.entity.ExamAttempt;
import com.example.oes.entity.Paper;
import com.example.oes.entity.PaperQuestion;
import com.example.oes.entity.Question;
import com.example.oes.mapper.AttemptAnswerMapper;
import com.example.oes.mapper.ExamAttemptMapper;
import com.example.oes.mapper.PaperMapper;
import com.example.oes.mapper.PaperQuestionMapper;
import com.example.oes.mapper.QuestionMapper;
import com.example.oes.service.JudgeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    private static final String SUBMITTED = "SUBMITTED";
    private static final String TIMEOUT = "TIMEOUT";
    private static final String GRADED = "GRADED";
    private static final String NEED_REVIEW_MARK = "|NEED_REVIEW";

    /** 简答最小字数门槛，低于此长度的回答按 0 分处理 */
    private static final int SHORT_MIN_LENGTH = 10;

    private final ExamAttemptMapper attemptMapper;
    private final AttemptAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperMapper paperMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeResult grade(Long attemptId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BizException(ResultCode.NOT_FOUND, "答卷不存在");
        }
        if (GRADED.equals(attempt.getStatus())) {
            return new GradeResult(attempt.getObjectiveScore(), attempt.getSubjectiveScore(),
                    attempt.getTotalScore(), attempt.getIsPass() == 1, hasShortAnswer(attempt));
        }
        if (!SUBMITTED.equals(attempt.getStatus()) && !TIMEOUT.equals(attempt.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "答卷尚未提交，无法判分");
        }

        List<AttemptAnswer> answers = answerMapper.selectByAttempt(attemptId);
        if (answers.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "答卷没有作答记录");
        }

        List<Long> questionIds = answers.stream().map(AttemptAnswer::getQuestionId).toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, Integer> fullScoreMap = paperQuestionMapper.selectList(
                        Wrappers.<PaperQuestion>lambdaQuery()
                                .eq(PaperQuestion::getPaperId, attempt.getPaperId()))
                .stream().collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore));

        int objective = 0;
        int subjective = 0;
        boolean needReview = false;
        List<AttemptAnswer> updates = new ArrayList<>(answers.size());

        for (AttemptAnswer answer : answers) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) {
                // 题目被物理删除（正常流程不会发生），按 0 分处理并留痕
                answer.setScore(0);
                answer.setJudgeType("AUTO");
                answer.setJudgeDetail("题目不存在");
                updates.add(answer);
                continue;
            }
            int full = fullScoreMap.getOrDefault(question.getId(), 0);
            List<String> correct = parseStringList(question.getAnswerJson());
            List<String> user = parseStringList(answer.getAnswerJson());
            QuestionType type = QuestionType.valueOf(question.getType());

            JudgeOutcome outcome = judgeOne(type, full, correct, user);
            answer.setScore(outcome.score());
            answer.setJudgeType(outcome.manual() ? "UNJUDGED" : "AUTO");
            answer.setJudgeDetail(outcome.detail());
            updates.add(answer);

            if (type == QuestionType.SHORT) {
                subjective += outcome.score();
                needReview = true;
            } else {
                objective += outcome.score();
            }
        }

        if (!updates.isEmpty()) {
            answerMapper.updateJudgeBatch(attemptId, updates);
        }

        int total = objective + subjective;
        Paper paper = paperMapper.selectById(attempt.getPaperId());
        boolean pass = paper != null && total >= paper.getPassScore();

        ExamAttempt update = new ExamAttempt();
        update.setId(attemptId);
        update.setObjectiveScore(objective);
        update.setSubjectiveScore(subjective);
        update.setTotalScore(total);
        update.setIsPass(pass ? 1 : 0);
        update.setStatus(GRADED);
        attemptMapper.updateById(update);

        log.info("attempt graded: id={}, objective={}, subjective={}, total={}, pass={}, needReview={}",
                attemptId, objective, subjective, total, pass, needReview);
        return new GradeResult(objective, subjective, total, pass, needReview);
    }

    // ---------------- 内部方法 ----------------

    private JudgeOutcome judgeOne(QuestionType type, int full, List<String> correct, List<String> user) {
        Set<String> correctSet = new LinkedHashSet<>(correct);
        Set<String> userSet = new LinkedHashSet<>(user);
        return switch (type) {
            case SINGLE, JUDGE -> {
                int score = ScoreRules.singleChoice(full, correctSet, userSet);
                yield new JudgeOutcome(score, false,
                        score > 0 ? "答案正确" : (userSet.isEmpty() ? "未作答" : "答案错误"));
            }
            case MULTIPLE -> {
                int score = ScoreRules.multipleChoice(full, correctSet, userSet);
                String detail = userSet.isEmpty() ? "未作答"
                        : score == full ? "全部选对"
                        : score > 0 ? "少选，按比例得分"
                        : "含错误选项或未选";
                yield new JudgeOutcome(score, false, detail);
            }
            case BLANK -> {
                // 多空题的每空可接受答案用 "|" 分隔，与导入格式一致
                List<Set<String>> accepted = new ArrayList<>();
                for (String item : correct) {
                    accepted.add(new LinkedHashSet<>(List.of(item.split("[|｜]"))));
                }
                int score = ScoreRules.blank(full, accepted, user);
                yield new JudgeOutcome(score, false, "填空题按空计分");
            }
            case SHORT -> {
                int score = ScoreRules.shortAnswer(full, correct, String.join("", user), SHORT_MIN_LENGTH);
                List<String> hits = ScoreRules.hitKeywords(correct, String.join("", user));
                String detail = "命中关键词 " + hits.size() + "/" + correct.size()
                        + (hits.isEmpty() ? "" : ": " + String.join(",", hits)) + NEED_REVIEW_MARK;
                yield new JudgeOutcome(score, true, detail);
            }
        };
    }

    private boolean hasShortAnswer(ExamAttempt attempt) {
        // 以 judge_type 判断：判分后客观题为 AUTO、简答题为 UNJUDGED；
        // 教师复核后简答题变 MANUAL，这里也随之不再要求复核。
        // 不能依赖 judge_detail 里的标记——它会被复核时的"教师复核"文案覆盖。
        return answerMapper.selectByAttempt(attempt.getId()).stream()
                .anyMatch(a -> "UNJUDGED".equals(a.getJudgeType()));
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("parse json failed: {}", json);
            return List.of();
        }
    }

    private record JudgeOutcome(int score, boolean manual, String detail) {
    }
}
