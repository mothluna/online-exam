package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.ResultCode;
import com.example.oes.common.util.ScoreUtil;
import com.example.oes.dto.ExamQuery;
import com.example.oes.dto.ExamSaveRequest;
import com.example.oes.entity.Exam;
import com.example.oes.entity.Paper;
import com.example.oes.mapper.ExamAttemptMapper;
import com.example.oes.mapper.ExamMapper;
import com.example.oes.mapper.PaperMapper;
import com.example.oes.mapper.PaperQuestionMapper;
import com.example.oes.service.ExamService;
import com.example.oes.vo.ExamVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";

    private final ExamMapper examMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final ExamAttemptMapper attemptMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ExamSaveRequest request, Long operatorId) {
        Paper paper = validatePaper(request);
        validateWindow(request);

        Exam exam = new Exam();
        exam.setPaperId(paper.getId());
        exam.setTitle(request.getTitle().trim());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setDurationSec(request.getDurationSec());
        exam.setAllowRetake(normalizeFlag(request.getAllowRetake()));
        exam.setShuffle(normalizeFlag(request.getShuffle()));
        exam.setStatus(DRAFT);
        exam.setCreatedBy(operatorId);
        examMapper.insert(exam);
        log.info("exam created: id={}, paperId={}", exam.getId(), paper.getId());
        return exam.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ExamSaveRequest request) {
        Exam exam = load(id);
        if (!DRAFT.equals(exam.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "考试已发布，不能再修改");
        }
        Paper paper = validatePaper(request);
        validateWindow(request);

        Exam update = new Exam();
        update.setId(id);
        update.setPaperId(paper.getId());
        update.setTitle(request.getTitle().trim());
        update.setStartTime(request.getStartTime());
        update.setEndTime(request.getEndTime());
        update.setDurationSec(request.getDurationSec());
        update.setAllowRetake(normalizeFlag(request.getAllowRetake()));
        update.setShuffle(normalizeFlag(request.getShuffle()));
        examMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Exam exam = load(id);
        if (!DRAFT.equals(exam.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "考试已发布");
        }
        Map<String, Object> fact = paperQuestionMapper.countAndSum(exam.getPaperId());
        long cnt = fact.get("cnt") == null ? 0 : ((Number) fact.get("cnt")).longValue();
        if (cnt == 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "所绑定的试卷还没有题目，无法发布考试");
        }
        Exam update = new Exam();
        update.setId(id);
        update.setStatus(PUBLISHED);
        examMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Exam exam = load(id);
        if (!DRAFT.equals(exam.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "已发布的考试不能删除");
        }
        examMapper.deleteById(id);
    }

    @Override
    public PageResult<ExamVO> page(ExamQuery query) {
        IPage<Exam> page = examMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Exam>lambdaQuery()
                        .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                                Exam::getStatus, query.getStatus())
                        .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                                Exam::getTitle, query.getKeyword())
                        .orderByDesc(Exam::getId));

        List<Exam> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), page.getCurrent(), page.getSize());
        }

        Map<Long, Paper> paperMap = paperMapper.selectBatchIds(
                        records.stream().map(Exam::getPaperId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Paper::getId, p -> p));

        Map<Long, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : attemptMapper.countByExamIds(
                records.stream().map(Exam::getId).toList())) {
            countMap.put(((Number) row.get("examId")).longValue(),
                    ((Number) row.get("cnt")).longValue());
        }

        List<ExamVO> vos = records.stream().map(e -> {
            Paper p = paperMap.get(e.getPaperId());
            return ExamVO.builder()
                    .id(e.getId())
                    .paperId(e.getPaperId())
                    .paperTitle(p == null ? null : p.getTitle())
                    .title(e.getTitle())
                    .startTime(e.getStartTime())
                    .endTime(e.getEndTime())
                    .durationSec(e.getDurationSec())
                    .allowRetake(e.getAllowRetake())
                    .shuffle(e.getShuffle())
                    .status(e.getStatus())
                    .totalScore(p == null ? null : ScoreUtil.toYuan(p.getTotalScore()))
                    .totalCount(p == null ? null : p.getTotalCount())
                    .attemptCount(countMap.getOrDefault(e.getId(), 0L))
                    .build();
        }).toList();
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ExamVO detail(Long id) {
        Exam exam = load(id);
        Paper paper = paperMapper.selectById(exam.getPaperId());
        return ExamVO.builder()
                .id(exam.getId())
                .paperId(exam.getPaperId())
                .paperTitle(paper == null ? null : paper.getTitle())
                .title(exam.getTitle())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .durationSec(exam.getDurationSec())
                .allowRetake(exam.getAllowRetake())
                .shuffle(exam.getShuffle())
                .status(exam.getStatus())
                .totalScore(paper == null ? null : ScoreUtil.toYuan(paper.getTotalScore()))
                .totalCount(paper == null ? null : paper.getTotalCount())
                .build();
    }

    // ---------------- 内部方法 ----------------

    private Exam load(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BizException(ResultCode.NOT_FOUND, "考试不存在");
        }
        return exam;
    }

    private Paper validatePaper(ExamSaveRequest request) {
        Paper paper = paperMapper.selectById(request.getPaperId());
        if (paper == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "试卷不存在");
        }
        if (!PUBLISHED.equals(paper.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "只能绑定已发布的试卷");
        }
        return paper;
    }

    private void validateWindow(ExamSaveRequest request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BizException(ResultCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        long window = Duration.between(request.getStartTime(), request.getEndTime()).getSeconds();
        if (request.getDurationSec() > window) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "考试时长不能超过开放时间窗（" + window + " 秒）");
        }
    }

    private int normalizeFlag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }
}
