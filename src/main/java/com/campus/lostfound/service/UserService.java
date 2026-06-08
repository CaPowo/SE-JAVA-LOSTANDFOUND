package com.campus.lostfound.service;

import com.campus.lostfound.mapper.UserMapper;
import com.campus.lostfound.model.User;
import com.campus.lostfound.util.MyBatisUtil;
import com.campus.lostfound.util.PasswordUtil;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 用户登录和默认账号初始化。
 */
public class UserService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void initDefaultAdmin() {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            if (mapper.countAll() == 0) {
                String salt = PasswordUtil.generateSalt();
                User admin = new User();
                admin.setId(UUID.randomUUID().toString());
                admin.setUsername("admin");
                admin.setSalt(salt);
                admin.setPasswordHash(PasswordUtil.hash("123456", salt));
                admin.setRole("ADMIN");
                admin.setCreatedAt(now());
                mapper.insert(admin);
            }
            session.commit();
        }
    }

    public boolean login(String username, String password) {
        if (isBlank(username) || password == null) {
            return false;
        }
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User user = mapper.findByUsername(username.trim());
            return user != null
                    && PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash());
        }
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
