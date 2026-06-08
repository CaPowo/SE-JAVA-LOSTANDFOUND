package com.campus.lostfound.mapper;

import com.campus.lostfound.model.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    void insert(User user);

    User findByUsername(@Param("username") String username);

    int updatePassword(@Param("id") String id,
                       @Param("passwordHash") String passwordHash,
                       @Param("salt") String salt);

    int countAll();
}
