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
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final ExamMapper examMapper;
    private final ExamAttemptMapper attemptMapper;
    private final AttemptAnswerMapper answerMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

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
        return buildAttempt(attempt, exam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAnswers(Long attemptId, Long userId, AnswerSaveRequest request) {
        ExamAttempt attempt = loadOwnedAttempt(attemptId, userId);
        if (!IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BizException(ResultCode.ALREADY_SUBMITTED, "试卷已提交，不能再修改答案");
        }
        if (LocalDateTime.now().isAfter(attempt.getDeadline())) {
            throw new BizException(ResultCode.ALREADY_SUBMITTED, "已超过截止时间，无法保存答案");
        }

        List<AttemptAnswer> rows = answerMapper.selectByAttempt(attemptId);
        Map<Long, Integer> sortNoMap = rows.stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, AttemptAnswer::getSortNo));
        Set<Long> validIds = new HashSet<>(sortNoMap.keySet());

        List<AttemptAnswer> upserts = new ArrayList<>(request.getItems().size());
        for (AnswerSaveRequest.Item item : request.getItems()) {
            if (!validIds.contains(item.getQuestionId())) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "题目不属于本试卷: " + item.getQuestionId());
            }
            AttemptAnswer row = new AttemptAnswer();
            row.setAttemptId(attemptId);
            row.setQuestionId(item.getQuestionId());
            row.setAnswerJson(toJson(item.getAnswer()));
            row.setSortNo(sortNoMap.get(item.getQuestionId()));
            upserts.add(row);
        }
        if (!upserts.isEmpty()) {
            answerMapper.upsertBatch(upserts);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResultVO submit(Long attemptId, Long userId) {
        ExamAttempt attempt = loadOwnedAttempt(attemptId, userId);
        if (!IN_PROGRESS.equals(attempt.getStatus())) {
            // 幂等：已交卷/已超时直接返回既有结果
            return toResult(attempt);
        }

        boolean expired = LocalDateTime.now().isAfter(attempt.getDeadline());
        String target = expired ? TIMEOUT : SUBMITTED;

        int affected = attemptMapper.casSubmit(attemptId, target,
                expired ? attempt.getDeadline() : LocalDateTime.now(), attempt.getVersion());
        if (affected == 0) {
            // 并发双提交：另一个线程已经跃迁，读回结果即可
            return toResult(attemptMapper.selectById(attemptId));
        }

        // 缓存清理必须放在事务提交后，否则回滚会留下"缓存已删、库里还有"的短暂不一致
        registerAfterCommit(() -> redis.delete(RedisKey.attemptDeadline(attemptId)));

        ExamAttempt latest = attemptMapper.selectById(attemptId);
        log.info("attempt submitted: id={}, status={}, expired={}", attemptId, target, expired);
        return toResult(latest);
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

    private SubmitResultVO toResult(ExamAttempt attempt) {
        return SubmitResultVO.builder()
                .attemptId(attempt.getId())
                .status(attempt.getStatus())
                .submitTime(attempt.getSubmitTime())
                .totalScore(ScoreUtil.toYuan(attempt.getTotalScore() == null ? 0 : attempt.getTotalScore()))
                .isPass(attempt.getIsPass())
                .needReview(!GRADED.equals(attempt.getStatus()))
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
