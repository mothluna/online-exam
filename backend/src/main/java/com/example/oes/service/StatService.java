package com.example.oes.service;

import com.example.oes.common.result.PageResult;
import com.example.oes.dto.ExamQuery;
import com.example.oes.vo.AttemptStatVO;
import com.example.oes.vo.ExamStatVO;

public interface StatService {

    /** 按考试聚合分页，固定 3 条 SQL */
    PageResult<ExamStatVO> pageExamStats(ExamQuery query);

    /** 某场考试的成绩明细，固定 3 条 SQL */
    PageResult<AttemptStatVO> pageAttempts(Long examId, long page, long size);
}
