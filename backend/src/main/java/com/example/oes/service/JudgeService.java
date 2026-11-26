package com.example.oes.service;

public interface JudgeService {

    /**
     * 对已交卷的答卷判分并落库（同一事务内完成明细与汇总）。
     * 幂等：已判分的答卷重复调用只会返回既有结果。
     */
    GradeResult grade(Long attemptId);

    record GradeResult(int objectiveScore, int subjectiveScore, int totalScore,
                       boolean pass, boolean needReview) {
    }
}
