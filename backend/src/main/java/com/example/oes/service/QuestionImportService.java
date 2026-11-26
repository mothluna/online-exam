package com.example.oes.service;

import com.example.oes.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionImportService {

    ImportResultVO importQuestions(MultipartFile file, Long operatorId);

    /** 导入模板（含一行示例） */
    byte[] template();
}
