package com.example.oes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oes.entity.QuestionCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuestionCategoryMapper extends BaseMapper<QuestionCategory> {

    @Select("SELECT COUNT(*) FROM question WHERE category_id = #{categoryId}")
    long countQuestions(@Param("categoryId") Long categoryId);

    @Select("SELECT COUNT(*) FROM question_category WHERE parent_id = #{parentId}")
    long countChildren(@Param("parentId") Long parentId);
}
