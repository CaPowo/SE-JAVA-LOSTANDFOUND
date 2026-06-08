package com.campus.lostfound.mapper;

import com.campus.lostfound.model.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    void insert(User user);

    User findByUsername(@Param("username") String username);

    int countAll();
}
