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

    public static final String DEFAULT_PASSWORD = "123456";

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
                admin.setPasswordHash(PasswordUtil.hash(DEFAULT_PASSWORD, salt));
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

    public boolean isDefaultPassword(String password) {
        return DEFAULT_PASSWORD.equals(password);
    }

    public void changePassword(String username, String currentPassword, String newPassword) {
        String trimmedUsername = username == null ? "" : username.trim();
        validatePasswordChange(trimmedUsername, currentPassword, newPassword);

        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User user = mapper.findByUsername(trimmedUsername);
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }
            if (!PasswordUtil.verify(currentPassword, user.getSalt(), user.getPasswordHash())) {
                throw new IllegalArgumentException("当前密码不正确");
            }
            if (PasswordUtil.verify(newPassword, user.getSalt(), user.getPasswordHash())) {
                throw new IllegalArgumentException("新密码不能和当前密码相同");
            }

            String salt = PasswordUtil.generateSalt();
            int updated = mapper.updatePassword(user.getId(), PasswordUtil.hash(newPassword, salt), salt);
            if (updated != 1) {
                throw new IllegalStateException("密码更新失败");
            }
            session.commit();
        }
    }

    private void validatePasswordChange(String username, String currentPassword, String newPassword) {
        if (isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new IllegalArgumentException("当前密码不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少 6 位");
        }
        if (DEFAULT_PASSWORD.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能继续使用默认密码 123456");
        }
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
