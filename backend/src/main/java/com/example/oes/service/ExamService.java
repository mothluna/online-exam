package com.example.oes.service;

import com.example.oes.common.result.PageResult;
import com.example.oes.dto.ExamQuery;
import com.example.oes.dto.ExamSaveRequest;
import com.example.oes.vo.ExamVO;

public interface ExamService {

    Long create(ExamSaveRequest request, Long operatorId);

    void update(Long id, ExamSaveRequest request);

    void publish(Long id);

    void delete(Long id);

    PageResult<ExamVO> page(ExamQuery query);

    ExamVO detail(Long id);
}
