package com.example.oes.service;

import com.example.oes.vo.ExportTaskVO;

import java.io.IOException;
import java.io.OutputStream;

public interface ExportService {

    /** 先探测规模：小数据直接流式返回，大数据转异步任务 */
    ExportPlan plan(Long examId);

    /** 流式写出，内存占用与行数无关 */
    void stream(Long examId, OutputStream out) throws IOException;

    ExportTaskVO task(String taskId);

    record ExportPlan(boolean direct, long total, String taskId, String fileName) {
    }
}
