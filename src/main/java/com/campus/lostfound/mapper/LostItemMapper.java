package com.campus.lostfound.mapper;

import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.LostItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LostItemMapper {

    void insert(LostItem item);

    int update(LostItem item);

    int markClaimed(@Param("id") String id,
                    @Param("claimer") String claimer,
                    @Param("claimTime") String claimTime,
                    @Param("updatedAt") String updatedAt);

    int delete(@Param("id") String id);

    LostItem findById(@Param("id") String id);

    List<LostItem> query(@Param("name") String name,
                         @Param("category") String category,
                         @Param("location") String location,
                         @Param("status") ItemStatus status);
}
