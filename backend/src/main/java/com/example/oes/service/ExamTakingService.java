package com.example.oes.service;

import com.example.oes.dto.AnswerSaveRequest;
import com.example.oes.vo.AttemptResultVO;
import com.example.oes.vo.AttemptVO;
import com.example.oes.vo.StudentExamVO;
import com.example.oes.vo.SubmitResultVO;

import java.util.List;

public interface ExamTakingService {

    /** 学生可参加的考试列表，带本人作答状态 */
    List<StudentExamVO> listForStudent(Long userId);

    /** 开考。重复开考抛 409 并回带已有答卷 ID */
    AttemptVO start(Long examId, Long userId);

    /** 续考/查看答卷（不含答案） */
    AttemptVO getAttempt(Long attemptId, Long userId);

    /** 批量暂存，幂等 */
    void saveAnswers(Long attemptId, Long userId, AnswerSaveRequest request);

    /** 交卷。幂等：重复提交返回同一结果，不重复判分 */
    SubmitResultVO submit(Long attemptId, Long userId);

    /**
     * 成绩单。正确答案在考试结束时间之前不下发，
     * 防止先交卷的考生把答案传给同场次还没考的人。
     */
    AttemptResultVO result(Long attemptId, Long userId);
}
