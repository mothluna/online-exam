package com.example.oes.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;
import com.example.oes.dto.AttemptExportRow;
import com.example.oes.entity.Exam;
import com.example.oes.mapper.ExamMapper;
import com.example.oes.mapper.StatMapper;
import com.example.oes.service.ExportService;
import com.example.oes.service.StorageService;
import com.example.oes.vo.ExportTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    /** 超过这个行数就转异步：同步流式超过 5000 行时，用户等待时间不可控 */
    private static final long SYNC_THRESHOLD = 5000;
    private static final int BATCH = 500;
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final StatMapper statMapper;
    private final ExamMapper examMapper;
    private final StorageService storageService;

    @Qualifier("exportExecutor")
    private final ThreadPoolTaskExecutor executor;

    /** 任务表放内存：单实例够用；多实例部署时需改为数据库或 Redis */
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public ExportPlan plan(Long examId) {
        Exam exam = loadExam(examId);
        long total = statMapper.countAttempts(examId);
        if (total == 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "该考试还没有答卷，无法导出");
        }
        String fileName = buildFileName(exam.getTitle());

        if (total <= SYNC_THRESHOLD) {
            return new ExportPlan(true, total, null, fileName);
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        Task task = new Task(taskId, examId, exam.getTitle(), total);
        tasks.put(taskId, task);
        executor.execute(() -> run(task));
        log.info("export queued: taskId={}, examId={}, rows={}", taskId, examId, total);
        return new ExportPlan(false, total, taskId, fileName);
    }

    @Override
    public void stream(Long examId, OutputStream out) throws IOException {
        // 分批写，内存里始终只有 BATCH 行；不调用 doWrite(List) 以免一次性装载全表
        try (ExcelWriter writer = EasyExcel.write(out, AttemptExportRow.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet("成绩").build();
            writeInBatches(writer, sheet, examId);
        }
    }

    @Override
    public ExportTaskVO task(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "导出任务不存在或已过期");
        }
        return ExportTaskVO.builder()
                .taskId(task.taskId)
                .status(task.status)
                .rowCount(task.rowCount)
                .url(task.url)
                .message(task.message)
                .createdAt(task.createdAt)
                .build();
    }

    // ---------------- 异步导出 ----------------

    private void run(Task task) {
        task.status = "RUNNING";
        File temp = null;
        try {
            temp = File.createTempFile("export-", ".xlsx");
            try (ExcelWriter writer = EasyExcel.write(temp, AttemptExportRow.class).build()) {
                WriteSheet sheet = EasyExcel.writerSheet("成绩").build();
                writeInBatches(writer, sheet, task.examId);
            }

            // 文件可达数 MB；异步路径只处理到万级行，整体读入内存可接受。
            // 若将来要支持十万级，应改为 MinIO 分片流式上传。
            byte[] bytes = Files.readAllBytes(temp.toPath());
            String objectKey = "export/" + LocalDate.now() + "/" + task.taskId + ".xlsx";
            storageService.upload(objectKey, bytes, XLSX);
            task.url = storageService.presignedUrl(objectKey, 60);
            task.status = "DONE";
            log.info("export done: taskId={}, rows={}, size={}KB",
                    task.taskId, task.rowCount, bytes.length / 1024);
        } catch (Exception e) {
            task.status = "FAILED";
            task.message = e.getMessage();
            log.error("export failed: taskId={}", task.taskId, e);
        } finally {
            if (temp != null && temp.exists() && !temp.delete()) {
                log.warn("temp file not deleted: {}", temp.getAbsolutePath());
            }
        }
    }

    private void writeInBatches(ExcelWriter writer, WriteSheet sheet, Long examId) {
        long offset = 0;
        while (true) {
            List<AttemptExportRow> rows = statMapper.exportRows(examId, offset, BATCH);
            if (rows.isEmpty()) {
                break;
            }
            writer.write(rows, sheet);
            offset += rows.size();
            if (rows.size() < BATCH) {
                break;
            }
        }
    }

    private Exam loadExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BizException(ResultCode.NOT_FOUND, "考试不存在");
        }
        return exam;
    }

    private String buildFileName(String title) {
        String safe = title == null ? "成绩" : title.replaceAll("[\\\\/:*?\"<>|]", "_");
        return "成绩单_" + safe + "_" + LocalDateTime.now().format(STAMP) + ".xlsx";
    }

    private static class Task {
        private final String taskId;
        private final Long examId;
        private final String title;
        private final long rowCount;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private volatile String status = "PENDING";
        private volatile String url;
        private volatile String message;

        private Task(String taskId, Long examId, String title, long rowCount) {
            this.taskId = taskId;
            this.examId = examId;
            this.title = title;
            this.rowCount = rowCount;
        }
    }
}
