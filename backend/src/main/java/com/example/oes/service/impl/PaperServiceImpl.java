package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.oes.common.enums.QuestionType;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.ResultCode;
import com.example.oes.common.util.ScoreUtil;
import com.example.oes.dto.PaperQuery;
import com.example.oes.dto.PaperQuestionAddRequest;
import com.example.oes.dto.PaperSaveRequest;
import com.example.oes.entity.Paper;
import com.example.oes.entity.PaperQuestion;
import com.example.oes.entity.PaperRule;
import com.example.oes.entity.Question;
import com.example.oes.mapper.PaperMapper;
import com.example.oes.mapper.PaperQuestionMapper;
import com.example.oes.mapper.PaperRuleMapper;
import com.example.oes.mapper.QuestionMapper;
import com.example.oes.service.PaperService;
import com.example.oes.vo.PaperQuestionVO;
import com.example.oes.vo.PaperVO;
import com.example.oes.vo.RandomizeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperServiceImpl implements PaperService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String RANDOM = "RANDOM";
    private static final String MANUAL = "MANUAL";

    private final PaperMapper paperMapper;
    private final PaperRuleMapper ruleMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PaperSaveRequest request, Long operatorId) {
        String composeType = normalizeComposeType(request.getComposeType());
        List<PaperRule> rules = buildRules(request, composeType);

        Paper paper = new Paper();
        paper.setTitle(request.getTitle().trim());
        paper.setComposeType(composeType);
        paper.setDurationSec(request.getDurationSec());
        paper.setStatus(DRAFT);
        paper.setCreatedBy(operatorId);
        paper.setTotalScore(0);
        paper.setTotalCount(0);
        paper.setPassScore(ScoreUtil.toCent(
                request.getPassScore() == null ? BigDecimal.ZERO : request.getPassScore()));

        if (RANDOM.equals(composeType)) {
            paper.setTotalScore(sumRuleScore(rules));
            paper.setTotalCount(sumRuleCount(rules));
        }
        checkPassScore(paper.getPassScore(), paper.getTotalScore(), RANDOM.equals(composeType));
        paperMapper.insert(paper);

        for (PaperRule rule : rules) {
            rule.setPaperId(paper.getId());
            ruleMapper.insert(rule);
        }
        log.info("paper created: id={}, type={}, total={}", paper.getId(), composeType, paper.getTotalScore());
        return paper.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PaperSaveRequest request) {
        Paper paper = loadPaper(id);
        assertDraft(paper, "修改");

        String composeType = normalizeComposeType(request.getComposeType());
        List<PaperRule> rules = buildRules(request, composeType);

        Paper update = new Paper();
        update.setId(id);
        update.setTitle(request.getTitle().trim());
        update.setComposeType(composeType);
        update.setDurationSec(request.getDurationSec());
        update.setPassScore(ScoreUtil.toCent(
                request.getPassScore() == null ? BigDecimal.ZERO : request.getPassScore()));

        if (RANDOM.equals(composeType)) {
            update.setTotalScore(sumRuleScore(rules));
            update.setTotalCount(sumRuleCount(rules));
        }
        checkPassScore(update.getPassScore(), update.getTotalScore() == null
                ? paper.getTotalScore() : update.getTotalScore(), RANDOM.equals(composeType));

        // 规则整体替换：先删后插，保持与请求一致
        ruleMapper.delete(Wrappers.<PaperRule>lambdaQuery().eq(PaperRule::getPaperId, id));
        for (PaperRule rule : rules) {
            rule.setPaperId(id);
            ruleMapper.insert(rule);
        }
        paperMapper.updateById(update);

        if (MANUAL.equals(composeType)) {
            recomputeTotals(paper);
        }
    }

    @Override
    public PageResult<PaperVO> page(PaperQuery query) {
        LambdaQueryWrapper<Paper> wrapper = Wrappers.<Paper>lambdaQuery()
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        Paper::getStatus, query.getStatus())
                .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        Paper::getTitle, query.getKeyword())
                .orderByDesc(Paper::getId);
        IPage<Paper> page = paperMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);

        List<PaperVO> records = page.getRecords().stream()
                .map(p -> PaperVO.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .composeType(p.getComposeType())
                        .totalScore(ScoreUtil.toYuan(p.getTotalScore() == null ? 0 : p.getTotalScore()))
                        .totalCount(p.getTotalCount())
                        .passScore(ScoreUtil.toYuan(p.getPassScore() == null ? 0 : p.getPassScore()))
                        .durationSec(p.getDurationSec())
                        .status(p.getStatus())
                        .createdAt(p.getCreatedAt())
                        .build())
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PaperVO detail(Long id) {
        Paper paper = loadPaper(id);
        List<PaperRule> rules = loadRules(id);
        Map<String, Object> fact = paperQuestionMapper.countAndSum(id);

        PaperVO vo = PaperVO.builder()
                .id(paper.getId())
                .title(paper.getTitle())
                .composeType(paper.getComposeType())
                .totalScore(ScoreUtil.toYuan(paper.getTotalScore() == null ? 0 : paper.getTotalScore()))
                .totalCount(paper.getTotalCount())
                .passScore(ScoreUtil.toYuan(paper.getPassScore() == null ? 0 : paper.getPassScore()))
                .durationSec(paper.getDurationSec())
                .status(paper.getStatus())
                .createdAt(paper.getCreatedAt())
                .actualCount(toLong(fact.get("cnt")))
                .actualScore(ScoreUtil.toYuan((int) toLong(fact.get("sumScore"))))
                .rules(rules.stream().map(this::toRuleVO).toList())
                .build();
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RandomizeVO randomize(Long id) {
        long start = System.currentTimeMillis();
        Paper paper = loadPaper(id);
        assertDraft(paper, "抽题");

        List<PaperRule> rules = loadRules(id);
        if (rules.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "该试卷没有抽题规则，无法随机组卷");
        }

        Set<Long> picked = new HashSet<>();
        List<PaperQuestion> rows = new ArrayList<>();
        int sortNo = 1;

        for (PaperRule rule : rules) {
            List<Long> pool = selectCandidateIds(rule);
            pool.removeIf(picked::contains);
            int need = rule.getQuestionCount();
            if (pool.size() < need) {
                throw new BizException(ResultCode.POOL_INSUFFICIENT,
                        QuestionType.of(rule.getType()).label() + " 可用题目仅 " + pool.size()
                                + " 道，需要 " + need + " 道，请先补充题库");
            }
            // 只洗前 need 个即可，但一次全洗的常数开销可忽略，代码更直白
            Collections.shuffle(pool, ThreadLocalRandom.current());
            for (int i = 0; i < need; i++) {
                Long questionId = pool.get(i);
                picked.add(questionId);
                PaperQuestion pq = new PaperQuestion();
                pq.setPaperId(id);
                pq.setQuestionId(questionId);
                pq.setScore(rule.getScorePerItem());
                pq.setSortNo(sortNo++);
                rows.add(pq);
            }
        }

        paperQuestionMapper.deleteByPaperId(id);
        try {
            paperQuestionMapper.insertBatch(rows);
        } catch (DuplicateKeyException e) {
            // 理论上不会发生（内存已去重），保留兜底提示
            throw new BizException(ResultCode.PARAM_INVALID, "抽题出现重复，请重试");
        }
        recomputeTotals(paper);

        long elapsed = System.currentTimeMillis() - start;
        log.info("paper randomized: id={}, count={}, score={}, elapsed={}ms",
                id, paper.getTotalCount(), paper.getTotalScore(), elapsed);
        return RandomizeVO.builder()
                .totalCount(paper.getTotalCount())
                .totalScore(ScoreUtil.toYuan(paper.getTotalScore()))
                .elapsedMs(elapsed)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addQuestions(Long id, PaperQuestionAddRequest request) {
        Paper paper = loadPaper(id);
        assertDraft(paper, "加题");

        List<Long> ids = request.getItems().stream()
                .map(PaperQuestionAddRequest.Item::getQuestionId)
                .distinct()
                .toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        Set<Long> existing = new HashSet<>(paperQuestionMapper.selectQuestionIds(id));
        List<PaperQuestion> rows = new ArrayList<>();
        int sortNo = existing.size() + 1;

        for (PaperQuestionAddRequest.Item item : request.getItems()) {
            Question q = questionMap.get(item.getQuestionId());
            if (q == null) {
                throw new BizException(ResultCode.NOT_FOUND, "题目不存在: " + item.getQuestionId());
            }
            if (q.getStatus() == null || q.getStatus() != 1) {
                throw new BizException(ResultCode.PARAM_INVALID, "题目已停用: " + item.getQuestionId());
            }
            if (!existing.add(q.getId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "题目已在试卷中: " + q.getId());
            }
            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(id);
            pq.setQuestionId(q.getId());
            pq.setScore(item.getScore() == null
                    ? q.getDefaultScore() : ScoreUtil.toCent(item.getScore()));
            pq.setSortNo(sortNo++);
            rows.add(pq);
        }
        paperQuestionMapper.insertBatch(rows);
        recomputeTotals(paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeQuestion(Long id, Long questionId) {
        Paper paper = loadPaper(id);
        assertDraft(paper, "移除题目");
        int deleted = paperQuestionMapper.delete(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, id)
                .eq(PaperQuestion::getQuestionId, questionId));
        if (deleted == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "该题目不在此试卷中");
        }
        recomputeTotals(paper);
    }

    @Override
    public List<PaperQuestionVO> questions(Long id) {
        loadPaper(id);
        List<PaperQuestion> rows = paperQuestionMapper.selectList(
                Wrappers.<PaperQuestion>lambdaQuery()
                        .eq(PaperQuestion::getPaperId, id)
                        .orderByAsc(PaperQuestion::getSortNo));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(
                        rows.stream().map(PaperQuestion::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));

        List<PaperQuestionVO> result = new ArrayList<>(rows.size());
        for (PaperQuestion row : rows) {
            Question q = questionMap.get(row.getQuestionId());
            if (q == null) {
                continue;
            }
            result.add(PaperQuestionVO.builder()
                    .questionId(q.getId())
                    .type(q.getType())
                    .typeLabel(QuestionType.valueOf(q.getType()).label())
                    .stem(brief(q.getStem()))
                    .score(ScoreUtil.toYuan(row.getScore()))
                    .sortNo(row.getSortNo())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Paper paper = loadPaper(id);
        assertDraft(paper, "发布");

        Map<String, Object> fact = paperQuestionMapper.countAndSum(id);
        long cnt = toLong(fact.get("cnt"));
        long sum = toLong(fact.get("sumScore"));

        if (cnt == 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "试卷还没有题目，无法发布");
        }
        if (cnt != paper.getTotalCount() || sum != paper.getTotalScore()) {
            // 事实校验：声明值与实际汇总不一致，宁可拒绝发布也不放一份总分错误的卷子出去
            throw new BizException(ResultCode.PAPER_SCORE_MISMATCH,
                    "试卷实际 " + cnt + " 题 / " + ScoreUtil.toYuan((int) sum).toPlainString()
                            + " 分，与设定 " + paper.getTotalCount() + " 题 / "
                            + ScoreUtil.toYuan(paper.getTotalScore()).toPlainString() + " 分不一致");
        }

        Paper update = new Paper();
        update.setId(id);
        update.setStatus(PUBLISHED);
        paperMapper.updateById(update);
        log.info("paper published: id={}, count={}, score={}", id, cnt, sum);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Paper paper = loadPaper(id);
        assertDraft(paper, "删除");
        paperQuestionMapper.deleteByPaperId(id);
        ruleMapper.delete(Wrappers.<PaperRule>lambdaQuery().eq(PaperRule::getPaperId, id));
        paperMapper.deleteById(id);
    }

    // ---------------- 内部方法 ----------------

    private Paper loadPaper(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BizException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        return paper;
    }

    private void assertDraft(Paper paper, String action) {
        if (!DRAFT.equals(paper.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "试卷已发布，不能再" + action);
        }
    }

    private List<PaperRule> loadRules(Long paperId) {
        return ruleMapper.selectList(Wrappers.<PaperRule>lambdaQuery()
                .eq(PaperRule::getPaperId, paperId)
                .orderByAsc(PaperRule::getSortNo)
                .orderByAsc(PaperRule::getId));
    }

    private String normalizeComposeType(String raw) {
        String v = raw == null ? RANDOM : raw.trim().toUpperCase();
        if (!RANDOM.equals(v) && !MANUAL.equals(v)) {
            throw new BizException(ResultCode.PARAM_INVALID, "组卷方式只能是 MANUAL 或 RANDOM");
        }
        return v;
    }

    /**
     * 规则校验：这里是"总分偏差为 0"的根。
     * 一旦规则合计与设定总分不等，直接拒绝，系统内不存在差 1 分的中间态。
     */
    private List<PaperRule> buildRules(PaperSaveRequest request, String composeType) {
        if (MANUAL.equals(composeType)) {
            return List.of();
        }
        if (request.getRules() == null || request.getRules().isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "按规则组卷必须填写抽题规则");
        }
        if (request.getTotalScore() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "按规则组卷必须填写设定总分");
        }

        List<PaperRule> rules = new ArrayList<>();
        long sumScore = 0;
        int sortNo = 0;
        for (PaperSaveRequest.RuleItem item : request.getRules()) {
            QuestionType type = QuestionType.of(item.getType());
            int per = ScoreUtil.toCent(item.getScorePerItem());
            if (per <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "每题分值必须大于 0");
            }
            sumScore += (long) per * item.getQuestionCount();
            if (sumScore > 10_000_000L) {
                throw new BizException(ResultCode.PARAM_INVALID, "试卷总分过大");
            }

            PaperRule rule = new PaperRule();
            rule.setType(type.name());
            rule.setQuestionCount(item.getQuestionCount());
            rule.setScorePerItem(per);
            rule.setCategoryIds(item.getCategoryIds() == null || item.getCategoryIds().isEmpty()
                    ? null
                    : item.getCategoryIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
            rule.setDifficulty(item.getDifficulty());
            rule.setSortNo(sortNo++);
            rules.add(rule);
        }

        int declared = ScoreUtil.toCent(request.getTotalScore());
        if (sumScore != declared) {
            BigDecimal diff = ScoreUtil.toYuan((int) (declared - sumScore));
            throw new BizException(ResultCode.PAPER_SCORE_MISMATCH,
                    "规则合计 " + ScoreUtil.toYuan((int) sumScore).toPlainString()
                            + " 分，与设定总分 " + ScoreUtil.toYuan(declared).toPlainString()
                            + " 分相差 " + (diff.signum() > 0 ? "+" : "") + diff.toPlainString() + " 分");
        }
        return rules;
    }

    private int sumRuleScore(List<PaperRule> rules) {
        long sum = rules.stream().mapToLong(r -> (long) r.getScorePerItem() * r.getQuestionCount()).sum();
        return (int) sum;
    }

    private int sumRuleCount(List<PaperRule> rules) {
        return rules.stream().mapToInt(PaperRule::getQuestionCount).sum();
    }

    private void checkPassScore(int passScore, int totalScore, boolean totalKnown) {
        if (passScore < 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "及格分不能为负");
        }
        if (totalKnown && passScore > totalScore) {
            throw new BizException(ResultCode.PARAM_INVALID, "及格分不能高于总分");
        }
    }

    private List<Long> selectCandidateIds(PaperRule rule) {
        LambdaQueryWrapper<Question> wrapper = Wrappers.<Question>lambdaQuery()
                .select(Question::getId)
                .eq(Question::getStatus, 1)
                .eq(Question::getType, rule.getType());
        List<Long> categoryIds = parseCategoryIds(rule.getCategoryIds());
        if (!categoryIds.isEmpty()) {
            wrapper.in(Question::getCategoryId, categoryIds);
        }
        if (rule.getDifficulty() != null) {
            wrapper.eq(Question::getDifficulty, rule.getDifficulty());
        }
        List<Object> ids = questionMapper.selectObjs(wrapper);
        List<Long> result = new ArrayList<>(ids.size());
        for (Object id : ids) {
            result.add(((Number) id).longValue());
        }
        return result;
    }

    private List<Long> parseCategoryIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    private void recomputeTotals(Paper paper) {
        Map<String, Object> fact = paperQuestionMapper.countAndSum(paper.getId());
        int count = (int) toLong(fact.get("cnt"));
        int score = (int) toLong(fact.get("sumScore"));

        Paper update = new Paper();
        update.setId(paper.getId());
        update.setTotalCount(count);
        update.setTotalScore(score);
        paperMapper.updateById(update);

        paper.setTotalCount(count);
        paper.setTotalScore(score);
    }

    private PaperVO.RuleVO toRuleVO(PaperRule rule) {
        String label;
        try {
            label = QuestionType.valueOf(rule.getType()).label();
        } catch (IllegalArgumentException e) {
            label = rule.getType();
        }
        return PaperVO.RuleVO.builder()
                .type(rule.getType())
                .typeLabel(label)
                .questionCount(rule.getQuestionCount())
                .scorePerItem(ScoreUtil.toYuan(rule.getScorePerItem()))
                .categoryIds(parseCategoryIds(rule.getCategoryIds()))
                .difficulty(rule.getDifficulty())
                .build();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private String brief(String text) {
        if (text == null) {
            return "";
        }
        String s = text.trim();
        return s.length() <= 80 ? s : s.substring(0, 80) + "...";
    }
}
