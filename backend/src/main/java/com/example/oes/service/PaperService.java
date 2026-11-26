package com.example.oes.service;

import com.example.oes.common.result.PageResult;
import com.example.oes.dto.PaperQuery;
import com.example.oes.dto.PaperQuestionAddRequest;
import com.example.oes.dto.PaperSaveRequest;
import com.example.oes.vo.PaperQuestionVO;
import com.example.oes.vo.PaperVO;
import com.example.oes.vo.RandomizeVO;

import java.util.List;

public interface PaperService {

    Long create(PaperSaveRequest request, Long operatorId);

    void update(Long id, PaperSaveRequest request);

    PageResult<PaperVO> page(PaperQuery query);

    PaperVO detail(Long id);

    /** 按规则抽题，覆盖试卷现有题目；同一试卷内不重复 */
    RandomizeVO randomize(Long id);

    /** 手工加题 */
    void addQuestions(Long id, PaperQuestionAddRequest request);

    void removeQuestion(Long id, Long questionId);

    List<PaperQuestionVO> questions(Long id);

    /** 发布前做总分事实校验，不一致直接拒绝 */
    void publish(Long id);

    void delete(Long id);
}
