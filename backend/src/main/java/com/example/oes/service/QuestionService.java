package com.example.oes.service;

import com.example.oes.common.result.PageResult;
import com.example.oes.dto.QuestionQuery;
import com.example.oes.dto.QuestionSaveRequest;
import com.example.oes.vo.QuestionVO;

public interface QuestionService {

    PageResult<QuestionVO> page(QuestionQuery query);

    QuestionVO detail(Long id);

    Long create(QuestionSaveRequest request, Long operatorId);

    void update(Long id, QuestionSaveRequest request);

    void delete(Long id);

    void changeStatus(Long id, Integer status);
}
