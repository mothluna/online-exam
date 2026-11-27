package com.example.oes.service.impl;

import com.alibaba.excel.EasyExcel;
import com.example.oes.common.enums.QuestionType;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.model.OptionItem;
import com.example.oes.common.result.ResultCode;
import com.example.oes.common.util.ScoreUtil;
import com.example.oes.common.util.StemNormalizer;
import com.example.oes.dto.ImportErrorRow;
import com.example.oes.dto.QuestionImportRow;
import com.example.oes.entity.Question;
import com.example.oes.entity.QuestionCategory;
import com.example.oes.mapper.QuestionCategoryMapper;
import com.example.oes.mapper.QuestionMapper;
import com.example.oes.service.QuestionImportService;
import com.example.oes.service.StorageService;
import com.example.oes.service.support.QuestionImportListener;
import com.example.oes.service.support.QuestionValidator;
import com.example.oes.vo.ImportResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportServiceImpl implements QuestionImportService {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_ERROR_PREVIEW = 20;
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final QuestionMapper questionMapper;
    private final QuestionCategoryMapper categoryMapper;
    private final QuestionValidator validator;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Override
    public ImportResultVO importQuestions(MultipartFile file, Long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "文件不能为空");
        }
        long start = System.currentTimeMillis();

        ImportContext context = new ImportContext(buildCategoryPathMap(), operatorId);
        QuestionImportListener.RowHandler handler = new QuestionImportListener.RowHandler() {
            @Override
            public void handle(int rowNo, QuestionImportRow row) {
                context.handle(rowNo, row);
            }

            @Override
            public void flush() {
                context.flush();
            }
        };
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, QuestionImportRow.class,
                            new QuestionImportListener(handler, BATCH_SIZE))
                    .sheet()
                    .doRead();
        } catch (IOException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "文件读取失败: " + e.getMessage());
        }

        keepOriginalFile(file);

        // 批处理阶段的失败行在 flush 时才产生，行号会晚于逐行阶段的记录，
        // 必须按行号重排，否则导出的失败明细顺序混乱、用户无法按行定位
        context.failures.sort(Comparator.comparing(ImportErrorRow::getRowNo));

        String errorFileKey = null;
        String errorFileUrl = null;
        if (!context.failures.isEmpty()) {
            byte[] bytes = writeErrorFile(context.failures);
            errorFileKey = "import/" + LocalDate.now().format(MONTH) + "/"
                    + UUID.randomUUID().toString().replace("-", "") + "-error.xlsx";
            storageService.upload(errorFileKey, bytes, XLSX);
            errorFileUrl = storageService.presignedUrl(errorFileKey, 15);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("import done: total={}, success={}, failed={}, elapsed={}ms",
                context.total, context.success, context.failures.size(), elapsed);

        List<String> preview = context.failures.stream()
                .limit(MAX_ERROR_PREVIEW)
                .map(e -> "第 " + e.getRowNo() + " 行: " + e.getReason())
                .toList();

        return ImportResultVO.builder()
                .total(context.total)
                .success(context.success)
                .failed(context.failures.size())
                .elapsedMs(elapsed)
                .errors(preview)
                .errorFileKey(errorFileKey)
                .errorFileUrl(errorFileUrl)
                .build();
    }

    @Override
    public byte[] template() {
        QuestionImportRow sample = new QuestionImportRow();
        sample.setCategoryPath("Java 基础/集合");
        sample.setType("单选");
        sample.setStem("下列哪个是接口关键字？");
        sample.setOptionA("class");
        sample.setOptionB("interface");
        sample.setAnswer("B");
        sample.setAnalysis("interface 用于定义接口");
        sample.setDifficulty("易");
        sample.setScore("2");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, QuestionImportRow.class).sheet("题目导入").doWrite(List.of(sample));
        return out.toByteArray();
    }

    // ---------------- 上下文：逐行处理与分批落库 ----------------

    private class ImportContext {

        private final Map<String, Long> categoryPathMap;
        private final Long operatorId;
        private final Set<String> seenHashes = new HashSet<>();
        private final List<PendingRow> buffer = new ArrayList<>(BATCH_SIZE);
        private final List<ImportErrorRow> failures = new ArrayList<>();
        private int total;
        private int success;

        private ImportContext(Map<String, Long> categoryPathMap, Long operatorId) {
            this.categoryPathMap = categoryPathMap;
            this.operatorId = operatorId;
        }

        private void handle(int rowNo, QuestionImportRow row) {
            total++;
            try {
                Question question = parse(rowNo, row);
                if (!seenHashes.add(question.getStemHash())) {
                    throw new BizException(ResultCode.DUPLICATE_STEM, "文件内题干重复");
                }
                buffer.add(new PendingRow(rowNo, question));
            } catch (BizException e) {
                failures.add(new ImportErrorRow(rowNo, brief(row.getStem()), e.getMessage()));
            } catch (Exception e) {
                log.warn("parse row {} failed", rowNo, e);
                failures.add(new ImportErrorRow(rowNo, brief(row.getStem()), "解析失败: " + e.getMessage()));
            }
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            List<String> hashes = buffer.stream().map(p -> p.question().getStemHash()).toList();
            Set<String> existing = new HashSet<>(questionMapper.selectExistingHashes(hashes));

            List<PendingRow> insertable = new ArrayList<>(buffer.size());
            for (PendingRow pending : buffer) {
                if (existing.contains(pending.question().getStemHash())) {
                    // 预检命中：不落库，直接进失败明细，避免用异常做流程控制
                    failures.add(new ImportErrorRow(pending.rowNo(),
                            brief(pending.question().getStem()), "与库中已有题目重复"));
                } else {
                    insertable.add(pending);
                }
            }
            if (!insertable.isEmpty()) {
                List<Question> entities = insertable.stream().map(PendingRow::question).toList();
                try {
                    questionMapper.insertBatch(entities);
                    success += entities.size();
                } catch (DuplicateKeyException e) {
                    // 并发导入时预检可能同时通过，此时逐条重试以精确定位冲突行
                    log.warn("batch insert conflict, fallback to row-by-row");
                    insertOneByOne(insertable);
                }
            }
            buffer.clear();
        }

        private void insertOneByOne(List<PendingRow> candidates) {
            for (PendingRow pending : candidates) {
                try {
                    questionMapper.insert(pending.question());
                    success++;
                } catch (DuplicateKeyException e) {
                    failures.add(new ImportErrorRow(pending.rowNo(),
                            brief(pending.question().getStem()), "与库中已有题目重复"));
                }
            }
        }

        private Question parse(int rowNo, QuestionImportRow row) {
            String path = row.getCategoryPath() == null ? "" : row.getCategoryPath().trim();
            if (path.isEmpty()) {
                throw new BizException(ResultCode.PARAM_INVALID, "分类路径为空");
            }
            Long categoryId = categoryPathMap.get(path);
            if (categoryId == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "分类不存在: " + path);
            }

            String stem = row.getStem() == null ? "" : row.getStem().trim();
            if (stem.isEmpty()) {
                throw new BizException(ResultCode.PARAM_INVALID, "题干为空");
            }

            QuestionType type = QuestionType.of(row.getType());
            List<OptionItem> options = parseOptions(row);
            List<String> answer = parseAnswer(type, row.getAnswer());
            validator.validate(type, options, answer);

            Question q = new Question();
            q.setCategoryId(categoryId);
            q.setType(type.name());
            q.setStem(stem);
            q.setStemHash(StemNormalizer.hash(stem));
            boolean choice = type == QuestionType.SINGLE || type == QuestionType.MULTIPLE;
            q.setOptionsJson(choice ? toJson(options) : null);
            q.setAnswerJson(toJson(answer));
            // 与手工新增保持一致：空数取答案数组长度
            q.setBlankCount(type == QuestionType.BLANK ? Math.max(1, answer.size()) : 1);
            q.setAnalysis(row.getAnalysis());
            q.setDifficulty(parseDifficulty(row.getDifficulty()));
            q.setDefaultScore(parseScore(row.getScore()));
            q.setStatus(1);
            q.setCreatedBy(operatorId);
            return q;
        }

        private List<OptionItem> parseOptions(QuestionImportRow row) {
            String[] keys = {"A", "B", "C", "D", "E", "F"};
            String[] contents = {row.getOptionA(), row.getOptionB(), row.getOptionC(),
                    row.getOptionD(), row.getOptionE(), row.getOptionF()};
            List<OptionItem> options = new ArrayList<>();
            for (int i = 0; i < keys.length; i++) {
                if (!validator.isBlank(contents[i])) {
                    options.add(new OptionItem(keys[i], contents[i].trim()));
                }
            }
            return options;
        }

        private List<String> parseAnswer(QuestionType type, String raw) {
            if (validator.isBlank(raw)) {
                throw new BizException(ResultCode.PARAM_INVALID, "答案为空");
            }
            String value = raw.trim();
            return switch (type) {
                case SINGLE, MULTIPLE -> parseChoiceAnswer(value);
                case JUDGE -> List.of(parseJudgeAnswer(value));
                case BLANK, SHORT -> parseTextAnswers(value);
            };
        }

        private List<String> parseChoiceAnswer(String raw) {
            String s = raw.replace('、', ',')
                    .replace('，', ',')
                    .replace('；', ',')
                    .replace(';', ',')
                    .replace(" ", "")
                    .toUpperCase();
            List<String> parts = Arrays.stream(s.split(","))
                    .filter(p -> !p.isBlank())
                    .toList();
            List<String> result = new ArrayList<>(parts);
            // 兼容 "AB" 这种连写形式
            if (result.size() == 1 && result.get(0).length() > 1 && result.get(0).matches("[A-Z]+")) {
                result = new ArrayList<>();
                for (char c : s.toCharArray()) {
                    result.add(String.valueOf(c));
                }
            }
            return result;
        }

        private String parseJudgeAnswer(String raw) {
            String s = raw.trim();
            if (s.equals("正确") || s.equals("对") || s.equalsIgnoreCase("true")
                    || s.equals("T") || s.equals("√") || s.equals("1")) {
                return "正确";
            }
            if (s.equals("错误") || s.equals("错") || s.equalsIgnoreCase("false")
                    || s.equals("F") || s.equals("×") || s.equals("0")) {
                return "错误";
            }
            throw new BizException(ResultCode.PARAM_INVALID, "判断题答案只能是「正确」或「错误」");
        }

        private List<String> parseTextAnswers(String raw) {
            return Arrays.stream(raw.split("[|｜]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        private Integer parseDifficulty(String raw) {
            if (validator.isBlank(raw)) {
                return 2;
            }
            return switch (raw.trim()) {
                case "易", "简单", "1" -> 1;
                case "中", "中等", "2" -> 2;
                case "难", "困难", "3" -> 3;
                default -> throw new BizException(ResultCode.PARAM_INVALID, "难度只能是 易/中/难");
            };
        }

        private Integer parseScore(String raw) {
            if (validator.isBlank(raw)) {
                throw new BizException(ResultCode.PARAM_INVALID, "分值为空");
            }
            String s = raw.trim().replace("分", "");
            try {
                BigDecimal value = new BigDecimal(s);
                if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(new BigDecimal("100")) > 0) {
                    throw new BizException(ResultCode.PARAM_INVALID, "分值需在 0.01~100 之间");
                }
                return ScoreUtil.toCent(value);
            } catch (NumberFormatException e) {
                throw new BizException(ResultCode.PARAM_INVALID, "分值为非法数字: " + raw);
            }
        }

        private String toJson(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "序列化失败");
            }
        }
    }

    // ---------------- 辅助 ----------------

    /** 构建"一级/二级"完整路径到分类 ID 的映射，一次查询完成 */
    private Map<String, Long> buildCategoryPathMap() {
        List<QuestionCategory> all = categoryMapper.selectList(null);
        Map<Long, QuestionCategory> byId = new HashMap<>();
        for (QuestionCategory c : all) {
            byId.put(c.getId(), c);
        }
        Map<String, Long> pathMap = new HashMap<>(all.size() * 2);
        for (QuestionCategory c : all) {
            Deque<String> names = new ArrayDeque<>();
            QuestionCategory cursor = c;
            int guard = 0;
            while (cursor != null && guard++ < 5) {
                names.addFirst(cursor.getName());
                cursor = byId.get(cursor.getParentId());
            }
            pathMap.put(String.join("/", names), c.getId());
        }
        return pathMap;
    }

    private void keepOriginalFile(MultipartFile file) {
        try {
            String key = "import/" + LocalDate.now().format(MONTH) + "/"
                    + UUID.randomUUID().toString().replace("-", "") + ".xlsx";
            storageService.upload(key, file.getBytes(), XLSX);
        } catch (Exception e) {
            // 留档失败不影响导入结果
            log.warn("keep original file failed: {}", e.getMessage());
        }
    }

    private byte[] writeErrorFile(List<ImportErrorRow> failures) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, ImportErrorRow.class).sheet("失败明细").doWrite(failures);
        return out.toByteArray();
    }

    private String brief(String text) {
        if (text == null) {
            return "";
        }
        String s = text.trim();
        return s.length() <= 60 ? s : s.substring(0, 60) + "...";
    }

    /** 待落库的一行：保留 Excel 行号，失败时才能准确回写 */
    private record PendingRow(int rowNo, Question question) {
    }
}
