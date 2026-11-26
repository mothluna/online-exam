package com.example.oes.service.impl;

import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.ResultCode;
import com.example.oes.dto.ExamQuery;
import com.example.oes.entity.Exam;
import com.example.oes.mapper.ExamMapper;
import com.example.oes.mapper.StatMapper;
import com.example.oes.service.StatService;
import com.example.oes.vo.AttemptStatVO;
import com.example.oes.vo.ExamStatVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {

    private final StatMapper statMapper;
    private final ExamMapper examMapper;

    @Override
    public PageResult<ExamStatVO> pageExamStats(ExamQuery query) {
        String status = blankToNull(query.getStatus());
        String keyword = blankToNull(query.getKeyword());
        long offset = (query.getPage() - 1) * query.getSize();

        // ① 总数
        long total = statMapper.countExams(status, keyword);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPage(), query.getSize());
        }
        // ② 当前页考试（已 JOIN 试卷总分）
        List<ExamStatVO> records = statMapper.pageExams(status, keyword, offset, query.getSize());
        if (records.isEmpty()) {
            return PageResult.of(List.of(), total, query.getPage(), query.getSize());
        }
        // ③ 本页考试的聚合指标，IN 列表长度等于页大小，与数据量无关
        Map<Long, Map<String, Object>> aggMap = new HashMap<>();
        for (Map<String, Object> row : statMapper.aggregateByExams(
                records.stream().map(ExamStatVO::getExamId).toList())) {
            aggMap.put(toLong(row.get("examId")), row);
        }

        for (ExamStatVO vo : records) {
            Map<String, Object> agg = aggMap.get(vo.getExamId());
            if (agg == null) {
                vo.setJoinCount(0);
                vo.setSubmitCount(0);
                vo.setPassCount(0);
                vo.setPassRate(BigDecimal.ZERO);
                continue;
            }
            long join = toLong(agg.get("joinCount"));
            long submit = toLong(agg.get("submitCount"));
            long pass = toLong(agg.get("passCount"));
            vo.setJoinCount(join);
            vo.setSubmitCount(submit);
            vo.setPassCount(pass);
            vo.setAvgScore(centToYuan(agg.get("avgScore")));
            vo.setMaxScore(centToYuan(agg.get("maxScore")));
            vo.setMinScore(centToYuan(agg.get("minScore")));
            vo.setPassRate(submit == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(pass * 100.0 / submit).setScale(1, RoundingMode.HALF_UP));
        }
        return PageResult.of(records, total, query.getPage(), query.getSize());
    }

    @Override
    public PageResult<AttemptStatVO> pageAttempts(Long examId, long page, long size) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BizException(ResultCode.NOT_FOUND, "考试不存在");
        }
        // ① 考试信息（上面已查） ② 总数 ③ 当前页明细
        long total = statMapper.countAttempts(examId);
        if (total == 0) {
            return PageResult.of(List.of(), 0, page, size);
        }
        List<AttemptStatVO> records = statMapper.pageAttempts(examId, (page - 1) * size, size);
        return PageResult.of(records, total, page, size);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private BigDecimal centToYuan(Object cent) {
        if (cent == null) {
            return null;
        }
        return BigDecimal.valueOf(((Number) cent).longValue(), 2);
    }
}
