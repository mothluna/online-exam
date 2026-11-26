package com.example.oes.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;
import com.example.oes.dto.CategorySaveRequest;
import com.example.oes.entity.QuestionCategory;
import com.example.oes.mapper.QuestionCategoryMapper;
import com.example.oes.mapper.QuestionMapper;
import com.example.oes.service.QuestionCategoryService;
import com.example.oes.vo.CategoryNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionCategoryServiceImpl implements QuestionCategoryService {

    private static final int MAX_LEVEL = 3;

    private final QuestionCategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;

    @Override
    public List<CategoryNode> tree() {
        List<QuestionCategory> all = categoryMapper.selectList(
                Wrappers.<QuestionCategory>lambdaQuery()
                        .orderByAsc(QuestionCategory::getParentId)
                        .orderByAsc(QuestionCategory::getSortNo)
                        .orderByAsc(QuestionCategory::getId));

        // 一次聚合拿到所有分类的题目数，避免逐个分类查 count 的 N+1
        Map<Long, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : questionMapper.countGroupByCategory()) {
            Object cid = row.get("categoryId");
            Object cnt = row.get("cnt");
            if (cid != null && cnt != null) {
                countMap.put(((Number) cid).longValue(), ((Number) cnt).longValue());
            }
        }

        Map<Long, CategoryNode> nodeMap = new HashMap<>();
        for (QuestionCategory c : all) {
            CategoryNode node = new CategoryNode();
            node.setId(c.getId());
            node.setParentId(c.getParentId());
            node.setName(c.getName());
            node.setSortNo(c.getSortNo());
            node.setStatus(c.getStatus());
            node.setQuestionCount(countMap.getOrDefault(c.getId(), 0L));
            nodeMap.put(c.getId(), node);
        }

        List<CategoryNode> roots = new ArrayList<>();
        for (QuestionCategory c : all) {
            CategoryNode node = nodeMap.get(c.getId());
            CategoryNode parent = nodeMap.get(c.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.addChild(node);
            }
        }
        roots.sort(Comparator.comparing(CategoryNode::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CategoryNode::getId));
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySaveRequest request) {
        Long parentId = normalizeParent(request.getParentId());
        checkParent(parentId);
        checkLevel(parentId);

        QuestionCategory entity = new QuestionCategory();
        entity.setParentId(parentId);
        entity.setName(request.getName().trim());
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        try {
            categoryMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.ALREADY_STARTED, "同级下已存在同名分类");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategorySaveRequest request) {
        QuestionCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        }
        Long parentId = normalizeParent(request.getParentId());
        if (parentId.equals(id)) {
            throw new BizException(ResultCode.PARAM_INVALID, "父分类不能是自己");
        }
        checkParent(parentId);
        checkLevel(parentId);
        if (!parentId.equals(existing.getParentId()) && isDescendant(id, parentId)) {
            throw new BizException(ResultCode.PARAM_INVALID, "不能把分类移动到自己的子分类下");
        }

        QuestionCategory update = new QuestionCategory();
        update.setId(id);
        update.setParentId(parentId);
        update.setName(request.getName().trim());
        update.setSortNo(request.getSortNo());
        update.setStatus(request.getStatus());
        try {
            categoryMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.ALREADY_STARTED, "同级下已存在同名分类");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QuestionCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        }
        if (categoryMapper.countChildren(id) > 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "该分类下还有子分类，不可删除");
        }
        long questionCount = categoryMapper.countQuestions(id);
        if (questionCount > 0) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "该分类下还有 " + questionCount + " 道题目，请先移动或删除题目");
        }
        categoryMapper.deleteById(id);
    }

    private Long normalizeParent(Long parentId) {
        return parentId == null || parentId < 0 ? 0L : parentId;
    }

    private void checkParent(Long parentId) {
        if (parentId == 0L) {
            return;
        }
        if (categoryMapper.selectById(parentId) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "父分类不存在");
        }
    }

    /** 题目要求分类最多 3 级，这里按父链长度判断 */
    private void checkLevel(Long parentId) {
        int level = 1;
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            level++;
            QuestionCategory parent = categoryMapper.selectById(cursor);
            if (parent == null) {
                break;
            }
            cursor = parent.getParentId();
        }
        if (level > MAX_LEVEL) {
            throw new BizException(ResultCode.PARAM_INVALID, "分类最多 3 级");
        }
    }

    private boolean isDescendant(Long ancestorId, Long candidateId) {
        Long cursor = candidateId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < MAX_LEVEL) {
            if (cursor.equals(ancestorId)) {
                return true;
            }
            QuestionCategory node = categoryMapper.selectById(cursor);
            if (node == null) {
                return false;
            }
            cursor = node.getParentId();
        }
        return false;
    }
}
