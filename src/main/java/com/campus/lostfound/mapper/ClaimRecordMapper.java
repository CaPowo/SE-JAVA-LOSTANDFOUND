package com.campus.lostfound.mapper;

import com.campus.lostfound.model.ClaimRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ClaimRecordMapper {

    void insert(ClaimRecord record);

    int deleteByItemId(@Param("itemId") String itemId);

    List<ClaimRecord> findByItemId(@Param("itemId") String itemId);
}
