package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.oes.common.enums.QuestionType;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.model.OptionItem;
import com.example.oes.common.result.PageResult;
import com.example.oes.common.result.ResultCode;
import com.example.oes.common.util.ScoreUtil;
import com.example.oes.common.util.StemNormalizer;
import com.example.oes.dto.QuestionQuery;
import com.example.oes.dto.QuestionSaveRequest;
import com.example.oes.entity.Question;
import com.example.oes.entity.QuestionCategory;
import com.example.oes.mapper.QuestionCategoryMapper;
import com.example.oes.mapper.QuestionMapper;
import com.example.oes.service.QuestionService;
import com.example.oes.service.support.QuestionValidator;
import com.example.oes.vo.QuestionVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionCategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;
    private final QuestionValidator validator;

    @Override
    public PageResult<QuestionVO> page(QuestionQuery query) {
        LambdaQueryWrapper<Question> wrapper = Wrappers.<Question>lambdaQuery()
                // 列表不查 analysis（TEXT），减少回表与网络传输
                .select(Question.class, f -> !"analysis".equals(f.getColumn()))
                .eq(query.getCategoryId() != null, Question::getCategoryId, query.getCategoryId())
                .eq(query.getType() != null && !query.getType().isBlank(),
                        Question::getType, query.getType() == null ? null : query.getType().trim().toUpperCase())
                .eq(query.getDifficulty() != null, Question::getDifficulty, query.getDifficulty())
                .eq(query.getStatus() != null, Question::getStatus, query.getStatus())
                .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        Question::getStem, query.getKeyword() == null ? null : query.getKeyword().trim())
                .orderByDesc(Question::getId);

        IPage<Question> page = questionMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);

        Map<Long, String> categoryNames = loadCategoryNames(
                page.getRecords().stream().map(Question::getCategoryId).collect(Collectors.toSet()));

        List<QuestionVO> records = page.getRecords().stream()
                .map(q -> toVO(q, categoryNames.get(q.getCategoryId()), false))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QuestionVO detail(Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw new BizException(ResultCode.NOT_FOUND, "题目不存在");
        }
        return toVO(q, loadCategoryName(q.getCategoryId()), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QuestionSaveRequest request, Long operatorId) {
        QuestionType type = QuestionType.of(request.getType());
        List<OptionItem> options = prepareAndValidate(type, request);
        checkCategory(request.getCategoryId());

        String hash = StemNormalizer.hash(request.getStem());
        Question duplicated = findDuplicated(hash, null);
        if (duplicated != null) {
            throw new BizException(ResultCode.DUPLICATE_STEM,
                    "题干与已有题目重复（ID " + duplicated.getId() + "）");
        }

        Question entity = new Question();
        fill(entity, type, request, options);
        entity.setStemHash(hash);
        entity.setCreatedBy(operatorId);
        try {
            questionMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发下预检可能同时通过，由 uk_stem_hash 仲裁
            throw new BizException(ResultCode.DUPLICATE_STEM, "题干与已有题目重复");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, QuestionSaveRequest request) {
        Question existing = questionMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "题目不存在");
        }
        QuestionType type = QuestionType.of(request.getType());
        List<OptionItem> options = prepareAndValidate(type, request);
        checkCategory(request.getCategoryId());

        String hash = StemNormalizer.hash(request.getStem());
        Question duplicated = findDuplicated(hash, id);
        if (duplicated != null) {
            throw new BizException(ResultCode.DUPLICATE_STEM,
                    "题干与已有题目重复（ID " + duplicated.getId() + "）");
        }

        Question update = new Question();
        update.setId(id);
        fill(update, type, request, options);
        update.setStemHash(hash);
        try {
            questionMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.DUPLICATE_STEM, "题干与已有题目重复");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Question existing = questionMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "题目不存在");
        }
        // 试卷模块上线后，此处需增加"被 paper_question 引用则拒绝删除"的校验
        questionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.PARAM_INVALID, "状态只能是 0 或 1");
        }
        if (questionMapper.selectById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "题目不存在");
        }
        Question update = new Question();
        update.setId(id);
        update.setStatus(status);
        questionMapper.updateById(update);
    }

    // ---------------- 内部方法 ----------------

    private List<OptionItem> prepareAndValidate(QuestionType type, QuestionSaveRequest request) {
        List<OptionItem> options = new ArrayList<>();
        if (request.getOptions() != null) {
            for (QuestionSaveRequest.Option o : request.getOptions()) {
                options.add(new OptionItem(
                        o.getKey() == null ? null : o.getKey().trim(),
                        o.getContent() == null ? null : o.getContent().trim()));
            }
        }
        List<String> answer = validator.normalizeAnswer(request.getAnswer());
        validator.validate(type, options, answer);
        request.setAnswer(answer);
        return options;
    }

    private void checkCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "分类不存在");
        }
    }

    private Question findDuplicated(String hash, Long excludeId) {
        return questionMapper.selectOne(Wrappers.<Question>lambdaQuery()
                .select(Question::getId, Question::getStem)
                .eq(Question::getStemHash, hash)
                .ne(excludeId != null, Question::getId, excludeId)
                .last("limit 1"));
    }

    private void fill(Question entity, QuestionType type, QuestionSaveRequest request, List<OptionItem> options) {
        entity.setCategoryId(request.getCategoryId());
        entity.setType(type.name());
        entity.setStem(request.getStem());
        entity.setAnswerJson(toJson(request.getAnswer()));
        boolean choice = type == QuestionType.SINGLE || type == QuestionType.MULTIPLE;
        entity.setOptionsJson(choice ? toJson(options) : null);
        // 空数由答案数组长度决定，与判分时逐空比对的口径一致
        entity.setBlankCount(type == QuestionType.BLANK ? Math.max(1, request.getAnswer().size()) : 1);
        entity.setAnalysis(request.getAnalysis());
        entity.setDifficulty(request.getDifficulty() == null ? 2 : request.getDifficulty());
        entity.setDefaultScore(ScoreUtil.toCent(request.getDefaultScore()));
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private QuestionVO toVO(Question q, String categoryName, boolean withAnswer) {
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setCategoryId(q.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setType(q.getType());
        vo.setTypeLabel(labelOf(q.getType()));
        vo.setStem(q.getStem());
        vo.setOptions(parseOptions(q.getOptionsJson()));
        vo.setAnalysis(q.getAnalysis());
        vo.setDifficulty(q.getDifficulty());
        vo.setScore(ScoreUtil.toYuan(q.getDefaultScore() == null ? 0 : q.getDefaultScore()));
        vo.setStatus(q.getStatus());
        vo.setCreatedAt(q.getCreatedAt());
        if (withAnswer) {
            vo.setAnswer(parseAnswer(q.getAnswerJson()));
        }
        return vo;
    }

    private String labelOf(String type) {
        try {
            return QuestionType.valueOf(type).label();
        } catch (IllegalArgumentException e) {
            return type;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "答案序列化失败");
        }
    }

    private List<QuestionVO.Option> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<OptionItem> raw = objectMapper.readValue(json, new TypeReference<List<OptionItem>>() {
            });
            List<QuestionVO.Option> result = new ArrayList<>(raw.size());
            for (OptionItem o : raw) {
                QuestionVO.Option vo = new QuestionVO.Option();
                vo.setKey(o.key());
                vo.setContent(o.content());
                result.add(vo);
            }
            return result;
        } catch (JsonProcessingException e) {
            log.warn("parse options failed: {}", json);
            return List.of();
        }
    }

    private List<String> parseAnswer(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("parse answer failed: {}", json);
            return List.of();
        }
    }

    private Map<Long, String> loadCategoryNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (QuestionCategory c : categoryMapper.selectBatchIds(ids)) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }

    private String loadCategoryName(Long id) {
        if (id == null) {
            return null;
        }
        QuestionCategory c = categoryMapper.selectById(id);
        return c == null ? null : c.getName();
    }
}
