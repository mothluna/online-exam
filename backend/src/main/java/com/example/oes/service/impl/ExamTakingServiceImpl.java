package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.oes.common.constant.RedisKey;
import com.example.oes.common.enums.QuestionType;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.model.OptionItem;
import com.example.oes.common.result.ResultCode;
import com.example.oes.common.util.ScoreUtil;
import com.example.oes.dto.AnswerSaveRequest;
import com.example.oes.entity.AttemptAnswer;
import com.example.oes.entity.Exam;
import com.example.oes.entity.ExamAttempt;
import com.example.oes.entity.Paper;
import com.example.oes.entity.PaperQuestion;
import com.example.oes.entity.Question;
import com.example.oes.mapper.AttemptAnswerMapper;
import com.example.oes.mapper.ExamAttemptMapper;
import com.example.oes.mapper.ExamMapper;
import com.example.oes.mapper.PaperMapper;
import com.example.oes.mapper.PaperQuestionMapper;
import com.example.oes.mapper.QuestionMapper;
import com.example.oes.service.ExamTakingService;
import com.example.oes.service.JudgeService;
import com.example.oes.vo.AttemptResultVO;
import com.example.oes.vo.AttemptVO;
import com.example.oes.vo.StudentExamVO;
import com.example.oes.vo.SubmitResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamTakingServiceImpl implements ExamTakingService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String SUBMITTED = "SUBMITTED";
    private static final String TIMEOUT = "TIMEOUT";
    private static final String GRADED = "GRADED";

    /** 判断题的唯一合法取值：与前端 radio 的 value、题库答案的存法保持一致 */
    private static final Set<String> JUDGE_VALUES = Set.of("正确", "错误");
    /** 简答答案上限，与前端 textarea 的 maxlength 对齐 */
    private static final int SHORT_MAX_LEN = 2000;
    /** 单空长度与最多空格数：只做防御，不据此判分 */
    private static final int BLANK_MAX_LEN = 200;
    private static final int BLANK_MAX_COUNT = 20;

    private final ExamMapper examMapper;
    private final ExamAttemptMapper attemptMapper;
    private final AttemptAnswerMapper answerMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final JudgeService judgeService;

    @Override
    public List<StudentExamVO> listForStudent(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Exam> exams = examMapper.selectList(Wrappers.<Exam>lambdaQuery()
                .eq(Exam::getStatus, PUBLISHED)
                .orderByDesc(Exam::getStartTime));
        if (exams.isEmpty()) {
            return List.of();
        }

        Map<Long, Paper> paperMap = paperMapper.selectBatchIds(
                        exams.stream().map(Exam::getPaperId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Paper::getId, p -> p));

        List<Long> examIds = exams.stream().map(Exam::getId).toList();
        Map<Long, ExamAttempt> attemptMap = new HashMap<>();
        for (ExamAttempt attempt : attemptMapper.selectList(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getUserId, userId)
                .in(ExamAttempt::getExamId, examIds))) {
            // 同一考试多次作答时保留 seq_no 最大的一条
            attemptMap.merge(attempt.getExamId(), attempt,
                    (a, b) -> a.getSeqNo() >= b.getSeqNo() ? a : b);
        }

        List<StudentExamVO> result = new ArrayList<>(exams.size());
        for (Exam exam : exams) {
            Paper paper = paperMap.get(exam.getPaperId());
            ExamAttempt attempt = attemptMap.get(exam.getId());
            // 只有已交卷的答卷才有得分：IN_PROGRESS 时 total_score 恒为 0，回给前端会被误读成"考了 0 分"
            boolean submitted = attempt != null && !IN_PROGRESS.equals(attempt.getStatus());
            result.add(StudentExamVO.builder()
                    .id(exam.getId())
                    .title(exam.getTitle())
                    .startTime(exam.getStartTime())
                    .endTime(exam.getEndTime())
                    .durationSec(exam.getDurationSec())
                    .totalScore(paper == null ? null : ScoreUtil.toYuan(paper.getTotalScore()))
                    .totalCount(paper == null ? null : paper.getTotalCount())
                    .state(resolveState(exam, attempt, now))
                    .attemptId(attempt == null ? null : attempt.getId())
                    .score(submitted ? ScoreUtil.toYuan(attempt.getTotalScore() == null ? 0 : attempt.getTotalScore()) : null)
                    .isPass(submitted ? attempt.getIsPass() : null)
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttemptVO start(Long examId, Long userId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BizException(ResultCode.NOT_FOUND, "考试不存在");
        }
        if (!PUBLISHED.equals(exam.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "考试尚未发布");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            throw new BizException(ResultCode.PARAM_INVALID, "考试尚未开始");
        }
        if (now.isAfter(exam.getEndTime())) {
            throw new BizException(ResultCode.PARAM_INVALID, "考试已结束");
        }

        // 第一层：Redis 前置拦截，挡掉并发重复点击；即使失败也不影响正确性
        String lockKey = RedisKey.examStartLock(examId, userId);
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
        if (!Boolean.TRUE.equals(locked)) {
            throw alreadyStarted(attemptMapper.selectLatest(examId, userId));
        }

        ExamAttempt attempt;
        try {
            ExamAttempt existing = attemptMapper.selectLatest(examId, userId);
            if (existing != null && (exam.getAllowRetake() == null || exam.getAllowRetake() != 1)) {
                throw alreadyStarted(existing);
            }
            int seqNo = existing == null ? 1 : existing.getSeqNo() + 1;

            LocalDateTime deadline = now.plusSeconds(exam.getDurationSec());
            if (deadline.isAfter(exam.getEndTime())) {
                deadline = exam.getEndTime();
            }

            attempt = new ExamAttempt();
            attempt.setExamId(examId);
            attempt.setUserId(userId);
            attempt.setPaperId(exam.getPaperId());
            attempt.setSeqNo(seqNo);
            attempt.setStatus(IN_PROGRESS);
            attempt.setStartTime(now);
            attempt.setDeadline(deadline);
            attempt.setObjectiveScore(0);
            attempt.setSubjectiveScore(0);
            attempt.setTotalScore(0);
            attempt.setIsPass(0);
            attempt.setVersion(0);
            attemptMapper.insert(attempt);
        } catch (DuplicateKeyException e) {
            // 第二层：数据库唯一键仲裁，并发下必然只有一个成功
            throw alreadyStarted(attemptMapper.selectLatest(examId, userId));
        } catch (RuntimeException e) {
            redis.delete(lockKey);
            throw e;
        }

        createAnswerSheet(attempt, exam);

        redis.opsForValue().set(RedisKey.attemptDeadline(attempt.getId()),
                String.valueOf(attempt.getDeadline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
                Duration.ofSeconds(exam.getDurationSec() + 600L));

        log.info("exam started: attemptId={}, examId={}, userId={}, deadline={}",
                attempt.getId(), examId, userId, attempt.getDeadline());
        return buildAttempt(attempt, exam);
    }

    @Override
    public AttemptVO getAttempt(Long attemptId, Long userId) {
        ExamAttempt attempt = loadOwnedAttempt(attemptId, userId);
        Exam exam = examMapper.selectById(attempt.getExamId());
        AttemptVO vo = buildAttempt(attempt, exam);

        // 每次进入作答页都换发会话号：后开的窗口接管，先开的窗口再保存时会被识别为过期。
        // TTL 取"距截止时间 + 5 分钟"，交卷后自然过期，不需要额外清理。
        String sessionId = UUID.randomUUID().toString();
        long ttl = Math.max(600, Duration.between(LocalDateTime.now(), attempt.getDeadline()).toSeconds() + 300);
        redis.opsForValue().set(RedisKey.attemptSession(attemptId), sessionId, Duration.ofSeconds(ttl));
        vo.setSessionId(sessionId);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> saveAnswers(Long attemptId, Long userId, AnswerSaveRequest request) {
        ExamAttempt attempt = loadOwnedAttempt(attemptId, userId);
        if (!IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BizException(ResultCode.ALREADY_SUBMITTED, "试卷已提交，不能再修改答案");
        }
        if (LocalDateTime.now().isAfter(attempt.getDeadline())) {
            throw new BizException(ResultCode.ALREADY_SUBMITTED, "已超过截止时间，无法保存答案");
        }

        // 多标签页保护：会话号与 Redis 中记录的不一致，说明另一个窗口后打开了作答页。
        // 这里拒绝写入，避免两个窗口各自的本地状态交替覆盖对方的答案。
        String currentSession = redis.opsForValue().get(RedisKey.attemptSession(attemptId));
        if (currentSession != null && request.getSessionId() != null
                && !currentSession.equals(request.getSessionId())) {
            throw new BizException(ResultCode.ATTEMPT_SESSION_CONFLICT,
                    "该答卷已在其他窗口打开，本窗口已停止自动保存；如需继续请刷新本页");
        }

        List<AttemptAnswer> rows = answerMapper.selectByAttempt(attemptId);
        Map<Long, Integer> sortNoMap = rows.stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, AttemptAnswer::getSortNo));
        Set<Long> validIds = new HashSet<>(sortNoMap.keySet());

        // 校验取值域需要题型与选项，一次批量取回，避免逐题查库
        Map<Long, Question> questionMap = validIds.isEmpty() ? Map.of()
                : questionMapper.selectBatchIds(validIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 被清洗过的题目：不阻断保存，但要让前端知道"这条没存成你发的样子"
        Set<Long> sanitized = new LinkedHashSet<>();
        List<AttemptAnswer> upserts = new ArrayList<>(request.getItems().size());
        for (AnswerSaveRequest.Item item : request.getItems()) {
            if (!validIds.contains(item.getQuestionId())) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "题目不属于本试卷: " + item.getQuestionId());
            }
            Question question = questionMap.get(item.getQuestionId());
            List<String> answer = question == null
                    ? item.getAnswer()
                    : sanitizeAnswer(question, item.getAnswer(), sanitized);

            AttemptAnswer row = new AttemptAnswer();
            row.setAttemptId(attemptId);
            row.setQuestionId(item.getQuestionId());
            row.setAnswerJson(toJson(answer));
            row.setSortNo(sortNoMap.get(item.getQuestionId()));
            upserts.add(row);
        }
        if (!upserts.isEmpty()) {
            answerMapper.upsertBatch(upserts);
        }
        return new ArrayList<>(sanitized);
    }

    /**
     * 按题型收敛答案取值。
     *
     * <p>这里刻意**不抛异常**：考试链路的第一原则是不阻断考生（见开发日志 3.3），
     * 客户端格式异常不应该让一次暂存整体失败、进而让考生以为答案丢了。
     * 做法是把非法值剔掉、超长值截断，并把受影响的题目回传给前端提示一次。
     *
     * <p>被剔掉的值与"未作答"在库里表现一致，这正是想要的：非法输入本来就不该有语义。
     */
    private List<String> sanitizeAnswer(Question question, List<String> raw, Set<Long> sanitized) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        QuestionType type = QuestionType.valueOf(question.getType());
        List<String> cleaned;
        switch (type) {
            case SINGLE -> {
                String value = raw.get(0);
                cleaned = validOptionKeys(question).contains(value) ? List.of(value) : List.of();
            }
            case MULTIPLE -> cleaned = raw.stream()
                    .filter(validOptionKeys(question)::contains)
                    .distinct()
                    .toList();
            case JUDGE -> {
                String value = raw.get(0);
                cleaned = JUDGE_VALUES.contains(value) ? List.of(value) : List.of();
            }
            case BLANK -> cleaned = raw.stream()
                    .limit(BLANK_MAX_COUNT)
                    .map(v -> truncate(v, BLANK_MAX_LEN))
                    .toList();
            case SHORT -> cleaned = List.of(truncate(raw.get(0), SHORT_MAX_LEN));
            default -> cleaned = raw;
        }
        // 单选/多选/判断被剔空，或数量被 limit 截断，都算"被清洗"
        if (cleaned.isEmpty() || cleaned.size() < raw.size()) {
            sanitized.add(question.getId());
        }
        return cleaned;
    }

    private Set<String> validOptionKeys(Question question) {
        return parseOptions(question.getOptionsJson()).stream()
                .map(AttemptVO.Option::getKey)
                .collect(Collectors.toSet());
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResultVO submit(Long attemptId, Long userId) {
        ExamAttempt attempt = loadOwnedAttempt(attemptId, userId);
        if (!IN_PROGRESS.equals(attempt.getStatus())) {
            // 幂等：已交卷直接返回既有结果；若上次判分未完成（如任务中断），这里顺带补齐。
            // 注意必须重新读库——判分改的是数据库，手上这份实体还是判分前的旧值
            JudgeService.GradeResult existing = judgeService.grade(attemptId);
            return toResult(attemptMapper.selectById(attemptId), existing);
        }

        boolean expired = LocalDateTime.now().isAfter(attempt.getDeadline());
        String target = expired ? TIMEOUT : SUBMITTED;

        int affected = attemptMapper.casSubmit(attemptId, target,
                expired ? attempt.getDeadline() : LocalDateTime.now(), attempt.getVersion());
        if (affected == 0) {
            // 并发双提交：另一个线程已经跃迁，读回结果即可
            ExamAttempt latest = attemptMapper.selectById(attemptId);
            return toResult(latest, judgeService.grade(attemptId));
        }

        // 判分与状态跃迁在同一事务内：任一步失败整体回滚，答卷回到 IN_PROGRESS 可重试
        JudgeService.GradeResult gradeResult = judgeService.grade(attemptId);

        // 缓存清理必须放在事务提交后，否则回滚会留下"缓存已删、库里还有"的短暂不一致
        registerAfterCommit(() -> redis.delete(RedisKey.attemptDeadline(attemptId)));

        ExamAttempt latest = attemptMapper.selectById(attemptId);
        log.info("attempt submitted: id={}, status={}, expired={}, total={}",
                attemptId, target, expired, latest.getTotalScore());
        return toResult(latest, gradeResult);
    }

    @Override
    public AttemptResultVO result(Long attemptId, Long userId) {
        ExamAttempt attempt = loadOwnedAttempt(attemptId, userId);
        if (IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "尚未交卷，暂无成绩");
        }

        Exam exam = examMapper.selectById(attempt.getExamId());
        Paper paper = paperMapper.selectById(attempt.getPaperId());
        LocalDateTime now = LocalDateTime.now();
        // 考试结束前只给总分：正确答案、逐题得分与对错都不下发
        boolean released = exam != null && exam.getEndTime() != null && !now.isBefore(exam.getEndTime());

        List<AttemptAnswer> answers = answerMapper.selectByAttempt(attemptId);
        Map<Long, Question> questionMap = answers.isEmpty() ? Map.of()
                : questionMapper.selectBatchIds(answers.stream().map(AttemptAnswer::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, Integer> fullScoreMap = paperQuestionMapper.selectList(
                        Wrappers.<PaperQuestion>lambdaQuery()
                                .eq(PaperQuestion::getPaperId, attempt.getPaperId()))
                .stream().collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore));

        boolean needReview = false;
        List<AttemptResultVO.QuestionResult> items = new ArrayList<>(answers.size());
        for (AttemptAnswer answer : answers) {
            Question q = questionMap.get(answer.getQuestionId());
            if (q == null) {
                continue;
            }
            int full = fullScoreMap.getOrDefault(q.getId(), 0);
            int got = answer.getScore() == null ? 0 : answer.getScore();
            boolean subjective = "SHORT".equals(q.getType());
            // 按明细判定"还有没有待复核的主观题"：教师复核后 judge_type 变为 MANUAL，
            // 若仍按题型判断，复核完的成绩页会一直提示"待复核"
            needReview = needReview || "UNJUDGED".equals(answer.getJudgeType());

            items.add(AttemptResultVO.QuestionResult.builder()
                    .questionId(q.getId())
                    .sortNo(answer.getSortNo())
                    .type(q.getType())
                    .typeLabel(QuestionType.valueOf(q.getType()).label())
                    .stem(q.getStem())
                    .options(parseOptions(q.getOptionsJson()).stream()
                            .map(o -> AttemptResultVO.Option.builder()
                                    .key(o.getKey())
                                    .content(o.getContent())
                                    .build())
                            .toList())
                    .myAnswer(parseAnswer(answer.getAnswerJson()))
                    .correctAnswer(released ? parseAnswer(q.getAnswerJson()) : null)
                    // 逐题得分同样属于答案信息：判断题答"对"得 0 分，等于直接公布答案是"错"
                    .score(released ? ScoreUtil.toYuan(got) : null)
                    .fullScore(ScoreUtil.toYuan(full))
                    .judgeType(answer.getJudgeType())
                    .fullMark(released && !subjective ? got >= full && full > 0 : null)
                    .build());
        }

        return AttemptResultVO.builder()
                .attemptId(attempt.getId())
                .examId(attempt.getExamId())
                .examTitle(exam == null ? null : exam.getTitle())
                .status(attempt.getStatus())
                .submitTime(attempt.getSubmitTime())
                .totalScore(ScoreUtil.toYuan(attempt.getTotalScore() == null ? 0 : attempt.getTotalScore()))
                .objectiveScore(ScoreUtil.toYuan(attempt.getObjectiveScore() == null ? 0 : attempt.getObjectiveScore()))
                .subjectiveScore(ScoreUtil.toYuan(attempt.getSubjectiveScore() == null ? 0 : attempt.getSubjectiveScore()))
                .passScore(ScoreUtil.toYuan(paper == null || paper.getPassScore() == null ? 0 : paper.getPassScore()))
                .isPass(attempt.getIsPass())
                .needReview(needReview)
                .answerVisible(released)
                .answerReleaseTime(exam == null ? null : exam.getEndTime())
                .questions(items)
                .build();
    }

    // ---------------- 内部方法 ----------------

    private void createAnswerSheet(ExamAttempt attempt, Exam exam) {
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                Wrappers.<PaperQuestion>lambdaQuery()
                        .eq(PaperQuestion::getPaperId, attempt.getPaperId())
                        .orderByAsc(PaperQuestion::getSortNo));
        if (paperQuestions.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "试卷没有题目，无法开考");
        }

        List<PaperQuestion> ordered = new ArrayList<>(paperQuestions);
        if (exam.getShuffle() != null && exam.getShuffle() == 1) {
            Collections.shuffle(ordered, ThreadLocalRandom.current());
        }

        List<AttemptAnswer> rows = new ArrayList<>(ordered.size());
        int sortNo = 1;
        for (PaperQuestion pq : ordered) {
            AttemptAnswer row = new AttemptAnswer();
            row.setAttemptId(attempt.getId());
            row.setQuestionId(pq.getQuestionId());
            row.setSortNo(sortNo++);
            rows.add(row);
        }
        answerMapper.insertBatch(rows);
    }

    private AttemptVO buildAttempt(ExamAttempt attempt, Exam exam) {
        List<AttemptAnswer> answers = answerMapper.selectByAttempt(attempt.getId());
        List<Long> questionIds = answers.stream().map(AttemptAnswer::getQuestionId).toList();
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Map.of()
                : questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, Integer> scoreMap = paperQuestionMapper.selectList(
                        Wrappers.<PaperQuestion>lambdaQuery()
                                .eq(PaperQuestion::getPaperId, attempt.getPaperId()))
                .stream().collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore));

        List<AttemptVO.QuestionVO> questions = new ArrayList<>(answers.size());
        List<AttemptVO.AnswerVO> answerVos = new ArrayList<>(answers.size());
        for (AttemptAnswer answer : answers) {
            Question q = questionMap.get(answer.getQuestionId());
            if (q == null) {
                continue;
            }
            List<AttemptVO.Option> options = parseOptions(q.getOptionsJson());
            questions.add(AttemptVO.QuestionVO.builder()
                    .questionId(q.getId())
                    .type(q.getType())
                    .typeLabel(QuestionType.valueOf(q.getType()).label())
                    .stem(q.getStem())
                    .options(options)
                    .score(ScoreUtil.toYuan(scoreMap.getOrDefault(q.getId(), 0)))
                    .sortNo(answer.getSortNo())
                    .blankCount(q.getBlankCount() == null ? 1 : q.getBlankCount())
                    .build());
            List<String> saved = parseAnswer(answer.getAnswerJson());
            if (!saved.isEmpty()) {
                answerVos.add(AttemptVO.AnswerVO.builder()
                        .questionId(q.getId())
                        .answer(saved)
                        .build());
            }
        }

        Paper paper = paperMapper.selectById(attempt.getPaperId());
        return AttemptVO.builder()
                .attemptId(attempt.getId())
                .examId(attempt.getExamId())
                .examTitle(exam == null ? null : exam.getTitle())
                .status(attempt.getStatus())
                .startTime(attempt.getStartTime())
                .deadline(attempt.getDeadline())
                .serverTime(LocalDateTime.now())
                .totalScore(paper == null ? null : ScoreUtil.toYuan(paper.getTotalScore()))
                .totalCount(questions.size())
                .questions(questions)
                .answers(answerVos)
                .build();
    }

    private ExamAttempt loadOwnedAttempt(Long attemptId, Long userId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BizException(ResultCode.NOT_FOUND, "答卷不存在");
        }
        if (!attempt.getUserId().equals(userId)) {
            // 已认证但访问他人资源：403
            throw new BizException(ResultCode.FORBIDDEN_RESOURCE);
        }
        return attempt;
    }

    private BizException alreadyStarted(ExamAttempt existing) {
        Map<String, Object> data = new HashMap<>();
        if (existing != null) {
            data.put("attemptId", existing.getId());
            data.put("status", existing.getStatus());
        }
        return new BizException(ResultCode.ALREADY_STARTED, "已开考，请继续作答", data);
    }

    private String resolveState(Exam exam, ExamAttempt attempt, LocalDateTime now) {
        if (attempt != null) {
            if (IN_PROGRESS.equals(attempt.getStatus())) {
                return "IN_PROGRESS";
            }
            if (SUBMITTED.equals(attempt.getStatus()) || TIMEOUT.equals(attempt.getStatus())
                    || GRADED.equals(attempt.getStatus())) {
                return "SUBMITTED";
            }
        }
        if (now.isBefore(exam.getStartTime())) {
            return "NOT_STARTED";
        }
        if (now.isAfter(exam.getEndTime())) {
            return "ENDED";
        }
        return "NOT_STARTED";
    }

    private SubmitResultVO toResult(ExamAttempt attempt, JudgeService.GradeResult gradeResult) {
        return SubmitResultVO.builder()
                .attemptId(attempt.getId())
                .status(attempt.getStatus())
                .submitTime(attempt.getSubmitTime())
                .totalScore(ScoreUtil.toYuan(attempt.getTotalScore() == null ? 0 : attempt.getTotalScore()))
                .isPass(attempt.getIsPass())
                .needReview(gradeResult != null && gradeResult.needReview())
                .build();
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private String toJson(List<String> answer) {
        if (answer == null || answer.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(answer);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "答案格式不正确");
        }
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

    private List<AttemptVO.Option> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<OptionItem> items = objectMapper.readValue(json, new TypeReference<List<OptionItem>>() {
            });
            List<AttemptVO.Option> result = new ArrayList<>(items.size());
            for (OptionItem item : items) {
                result.add(AttemptVO.Option.builder()
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
