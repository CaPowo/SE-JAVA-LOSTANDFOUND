package com.campus.lostfound.service;

import com.campus.lostfound.dao.UserDao;
import com.campus.lostfound.model.User;
import com.campus.lostfound.util.PasswordUtil;

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

    private final UserDao userDao = new UserDao();

    public void initDefaultAdmin() {
        if (userDao.countAll() == 0) {
            String salt = PasswordUtil.generateSalt();
            User admin = new User();
            admin.setId(UUID.randomUUID().toString());
            admin.setUsername("admin");
            admin.setSalt(salt);
            admin.setPasswordHash(PasswordUtil.hash(DEFAULT_PASSWORD, salt));
            admin.setRole("ADMIN");
            admin.setCreatedAt(now());
            userDao.insert(admin);
        }
    }

    public boolean login(String username, String password) {
        if (isBlank(username) || password == null) {
            return false;
        }
        User user = userDao.findByUsername(username.trim());
        return user != null
                && PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash());
    }

    public boolean isDefaultPassword(String password) {
        return DEFAULT_PASSWORD.equals(password);
    }

    public void changePassword(String username, String currentPassword, String newPassword) {
        String trimmedUsername = username == null ? "" : username.trim();
        validatePasswordChange(trimmedUsername, currentPassword, newPassword);

        User user = userDao.findByUsername(trimmedUsername);
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
        int updated = userDao.updatePassword(user.getId(), PasswordUtil.hash(newPassword, salt), salt);
        if (updated != 1) {
            throw new IllegalStateException("密码更新失败");
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
