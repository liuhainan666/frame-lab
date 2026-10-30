package com.framelab.template;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TemplateMapper {

    List<CategoryRow> findCategories(@Param("locale") String locale);

    List<TemplateRow> findPublished(@Param("categoryId") String categoryId, @Param("locale") String locale);

    TemplateRow findPublishedById(@Param("id") String id, @Param("locale") String locale);
}
