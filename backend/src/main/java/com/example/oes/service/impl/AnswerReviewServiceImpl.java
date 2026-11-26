package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.oes.common.enums.QuestionType;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.model.OptionItem;
import com.example.oes.common.result.ResultCode;
import com.example.oes.common.util.ScoreUtil;
import com.example.oes.dto.ReviewRequest;
import com.example.oes.entity.AttemptAnswer;
import com.example.oes.entity.Exam;
import com.example.oes.entity.ExamAttempt;
import com.example.oes.entity.Paper;
import com.example.oes.entity.PaperQuestion;
import com.example.oes.entity.Question;
import com.example.oes.entity.SysUser;
import com.example.oes.mapper.AttemptAnswerMapper;
import com.example.oes.mapper.ExamAttemptMapper;
import com.example.oes.mapper.ExamMapper;
import com.example.oes.mapper.PaperMapper;
import com.example.oes.mapper.PaperQuestionMapper;
import com.example.oes.mapper.QuestionMapper;
import com.example.oes.mapper.SysUserMapper;
import com.example.oes.security.LoginUser;
import com.example.oes.security.UserContext;
import com.example.oes.service.AnswerReviewService;
import com.example.oes.vo.AttemptDetailVO;
import com.example.oes.vo.AttemptResultVO;
import com.example.oes.vo.ReviewResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerReviewServiceImpl implements AnswerReviewService {

    private static final String SHORT = "SHORT";
    private static final String UNJUDGED = "UNJUDGED";
    private static final String MANUAL = "MANUAL";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String NEED_REVIEW_MARK = "|NEED_REVIEW";
    private static final String MACHINE_PREFIX = "；机器初判：";
    private static final int MAX_DETAIL = 500;

    private final ExamAttemptMapper attemptMapper;
    private final AttemptAnswerMapper answerMapper;
    private final ExamMapper examMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AttemptDetailVO detail(Long attemptId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BizException(ResultCode.NOT_FOUND, "答卷不存在");
        }
        Exam exam = examMapper.selectById(attempt.getExamId());
        checkOwner(exam);

        Paper paper = paperMapper.selectById(attempt.getPaperId());
        SysUser student = userMapper.selectById(attempt.getUserId());

        List<AttemptAnswer> answers = answerMapper.selectByAttempt(attemptId);
        Map<Long, Question> questionMap = answers.isEmpty() ? Map.of()
                : questionMapper.selectBatchIds(answers.stream().map(AttemptAnswer::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, Integer> fullScoreMap = paper == null ? Map.of()
                : paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                        .eq(PaperQuestion::getPaperId, paper.getId()))
                .stream().collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore));

        int pending = 0;
        List<AttemptDetailVO.AnswerDetail> items = new ArrayList<>(answers.size());
        for (AttemptAnswer answer : answers) {
            Question q = questionMap.get(answer.getQuestionId());
            if (q == null) {
                continue;
            }
            if (SHORT.equals(q.getType()) && UNJUDGED.equals(answer.getJudgeType())) {
                pending++;
            }
            items.add(AttemptDetailVO.AnswerDetail.builder()
                    .answerId(answer.getId())
                    .questionId(q.getId())
                    .sortNo(answer.getSortNo())
                    .type(q.getType())
                    .typeLabel(QuestionType.valueOf(q.getType()).label())
                    .stem(q.getStem())
                    .options(parseOptions(q.getOptionsJson()))
                    .myAnswer(parseAnswer(answer.getAnswerJson()))
                    .correctAnswerText(correctAnswerText(q, parseAnswer(q.getAnswerJson())))
                    .score(ScoreUtil.toYuan(answer.getScore() == null ? 0 : answer.getScore()))
                    .fullScore(ScoreUtil.toYuan(fullScoreMap.getOrDefault(q.getId(), 0)))
                    .judgeType(answer.getJudgeType())
                    .judgeDetail(answer.getJudgeDetail())
                    .build());
        }

        int objective = attempt.getObjectiveScore() == null ? 0 : attempt.getObjectiveScore();
        int subjective = attempt.getSubjectiveScore() == null ? 0 : attempt.getSubjectiveScore();
        return AttemptDetailVO.builder()
                .attemptId(attempt.getId())
                .examId(attempt.getExamId())
                .examTitle(exam == null ? null : exam.getTitle())
                .userId(attempt.getUserId())
                .username(student == null ? null : student.getUsername())
                .realName(student == null ? null : student.getRealName())
                .status(attempt.getStatus())
                .submitTime(attempt.getSubmitTime())
                .objectiveScore(ScoreUtil.toYuan(objective))
                .subjectiveScore(ScoreUtil.toYuan(subjective))
                .totalScore(ScoreUtil.toYuan(objective + subjective))
                .passScore(ScoreUtil.toYuan(paper == null || paper.getPassScore() == null ? 0 : paper.getPassScore()))
                .isPass(attempt.getIsPass())
                .needReview(pending > 0)
                .pendingReviewCount(pending)
                .answers(items)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewResultVO review(Long answerId, ReviewRequest request) {
        AttemptAnswer answer = answerMapper.selectById(answerId);
        if (answer == null) {
            throw new BizException(ResultCode.NOT_FOUND, "作答记录不存在");
        }
        ExamAttempt attempt = attemptMapper.selectById(answer.getAttemptId());
        if (attempt == null) {
            throw new BizException(ResultCode.NOT_FOUND, "答卷不存在");
        }
        if (IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "答卷尚未交卷，不能复核");
        }
        Exam exam = examMapper.selectById(attempt.getExamId());
        checkOwner(exam);

        Question question = questionMapper.selectById(answer.getQuestionId());
        if (question == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "题目不存在，无法复核");
        }
        if (!SHORT.equals(question.getType())) {
            throw new BizException(ResultCode.PARAM_INVALID, "客观题由系统自动判分，不需要人工复核");
        }

        PaperQuestion paperQuestion = paperQuestionMapper.selectOne(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, attempt.getPaperId())
                .eq(PaperQuestion::getQuestionId, question.getId()));
        int full = paperQuestion == null || paperQuestion.getScore() == null ? 0 : paperQuestion.getScore();
        int cent = toCent(request.getScore());
        if (cent > full) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "得分不能超过本题满分 " + ScoreUtil.toYuan(full).toPlainString() + " 分");
        }

        AttemptAnswer update = new AttemptAnswer();
        update.setId(answerId);
        update.setScore(cent);
        update.setJudgeType(MANUAL);
        update.setJudgeDetail(buildDetail(answer.getJudgeDetail(), cent, request.getComment()));
        answerMapper.updateById(update);

        ReviewResultVO result = recalc(attempt.getId());
        log.info("answer reviewed: answerId={}, attemptId={}, score={}, total={}, pending={}",
                answerId, attempt.getId(), cent, result.getTotalScore(), result.getPendingReviewCount());
        return result;
    }

    // ---------------- 内部方法 ----------------

    /**
     * 按最新明细重算答卷汇总。与自动判分的口径保持一致：
     * 客观分来自非 SHORT 题，主观分来自 SHORT 题，总分=两者之和，及格线取试卷快照。
     */
    private ReviewResultVO recalc(Long attemptId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        List<AttemptAnswer> answers = answerMapper.selectByAttempt(attemptId);
        Map<Long, Question> questionMap = answers.isEmpty() ? Map.of()
                : questionMapper.selectBatchIds(answers.stream().map(AttemptAnswer::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));

        int objective = 0;
        int subjective = 0;
        int pending = 0;
        for (AttemptAnswer answer : answers) {
            Question q = questionMap.get(answer.getQuestionId());
            boolean shortType = q != null && SHORT.equals(q.getType());
            int score = answer.getScore() == null ? 0 : answer.getScore();
            if (shortType) {
                subjective += score;
                if (UNJUDGED.equals(answer.getJudgeType())) {
                    pending++;
                }
            } else {
                objective += score;
            }
        }

        int total = objective + subjective;
        Paper paper = paperMapper.selectById(attempt.getPaperId());
        boolean pass = paper != null && paper.getPassScore() != null && total >= paper.getPassScore();

        ExamAttempt update = new ExamAttempt();
        update.setId(attemptId);
        update.setObjectiveScore(objective);
        update.setSubjectiveScore(subjective);
        update.setTotalScore(total);
        update.setIsPass(pass ? 1 : 0);
        attemptMapper.updateById(update);

        return ReviewResultVO.builder()
                .attemptId(attemptId)
                .subjectiveScore(ScoreUtil.toYuan(subjective))
                .totalScore(ScoreUtil.toYuan(total))
                .isPass(pass ? 1 : 0)
                .pendingReviewCount(pending)
                .needReview(pending > 0)
                .build();
    }

    /** 教师只能复核自己创建的考试；管理员放行 */
    private void checkOwner(Exam exam) {
        LoginUser user = UserContext.require();
        if (user.isAdmin()) {
            return;
        }
        if (exam == null || exam.getCreatedBy() == null || !exam.getCreatedBy().equals(user.userId())) {
            throw new BizException(ResultCode.FORBIDDEN_RESOURCE);
        }
    }

    /** 元 → 0.01 分整数；拒绝三位及以上小数，避免静默四舍五入 */
    private int toCent(BigDecimal yuan) {
        if (yuan == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "得分不能为空");
        }
        try {
            return yuan.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "得分最多两位小数");
        }
    }

    private String buildDetail(String previous, int cent, String comment) {
        StringBuilder sb = new StringBuilder("教师复核 → ")
                .append(ScoreUtil.toYuan(cent).toPlainString()).append(" 分");
        if (comment != null && !comment.isBlank()) {
            sb.append("：").append(comment.trim());
        }
        String machine = machineDetail(previous);
        if (machine != null) {
            sb.append(MACHINE_PREFIX).append(machine);
        }
        String text = sb.toString();
        return text.length() > MAX_DETAIL ? text.substring(0, MAX_DETAIL) : text;
    }

    /**
     * 从已有判分依据里剥离出"机器初判"部分。
     * 若每次复核都整段拼接 previous，改两次分后依据会变成
     * 「教师复核 8.5 → 机器初判：教师复核 9.0 → 机器初判：命中关键词…」的俄罗斯套娃。
     */
    private String machineDetail(String previous) {
        if (previous == null || previous.isBlank()) {
            return null;
        }
        int idx = previous.lastIndexOf(MACHINE_PREFIX);
        if (idx >= 0) {
            String tail = previous.substring(idx + MACHINE_PREFIX.length()).trim();
            return tail.isEmpty() ? null : tail;
        }
        String cleaned = previous.replace(NEED_REVIEW_MARK, "").trim();
        if (cleaned.isEmpty() || cleaned.startsWith("教师复核 →")) {
            return null;
        }
        return cleaned;
    }

    /** 简答题的参考答案是关键词列表，拼成一行给教师看 */
    private String correctAnswerText(Question q, List<String> correct) {
        if (!SHORT.equals(q.getType())) {
            return null;
        }
        return correct.isEmpty() ? null : String.join("；", correct);
    }

    private List<String> parseAnswer(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<AttemptResultVO.Option> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<OptionItem> items = objectMapper.readValue(json, new TypeReference<List<OptionItem>>() {
            });
            List<AttemptResultVO.Option> result = new ArrayList<>(items.size());
            for (OptionItem item : items) {
                result.add(AttemptResultVO.Option.builder()
                        .key(item.key())
                        .content(item.content())
                        .build());
            }
            return result;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
