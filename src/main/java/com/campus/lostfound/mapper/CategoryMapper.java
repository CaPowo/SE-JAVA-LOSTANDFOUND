package com.campus.lostfound.mapper;

import com.campus.lostfound.model.Category;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CategoryMapper {

    void insert(Category category);

    int updateName(@Param("id") String id,
                   @Param("name") String name);

    int delete(@Param("id") String id);

    void linkItemCategory(@Param("itemId") String itemId,
                          @Param("categoryId") String categoryId);

    int unlinkItem(@Param("itemId") String itemId);

    List<Category> findAll();

    Category findById(@Param("id") String id);

    Category findByName(@Param("name") String name);

    int countItemLinks(@Param("id") String id);
}
