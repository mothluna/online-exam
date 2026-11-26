package com.example.oes.service;

import com.example.oes.dto.ReviewRequest;
import com.example.oes.vo.AttemptDetailVO;
import com.example.oes.vo.ReviewResultVO;

/**
 * 主观题人工复核。
 *
 * <p>自动判分对简答题只做关键词粗筛（见 D-019），成绩最终以教师复核为准。
 * 复核是"改分"而不是"重判"：只覆盖被复核那一题，其余题目保持判分结果，
 * 然后按最新明细重算答卷汇总（客观分、主观分、总分、是否及格）。
 */
public interface AnswerReviewService {

    /** 教师端答卷详情（含答案与判分依据） */
    AttemptDetailVO detail(Long attemptId);

    /** 给一道主观题打分并重算答卷汇总 */
    ReviewResultVO review(Long answerId, ReviewRequest request);
}
