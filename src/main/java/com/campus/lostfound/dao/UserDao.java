package com.campus.lostfound.dao;

import com.campus.lostfound.mapper.UserMapper;
import com.campus.lostfound.model.User;
import com.campus.lostfound.util.MyBatisUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * 用户表 CRUD。Service 只调用这里,不直接接触 MyBatis Mapper。
 */
public class UserDao {

    public int countAll() {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            return session.getMapper(UserMapper.class).countAll();
        }
    }

    public void insert(User user) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            session.getMapper(UserMapper.class).insert(user);
            session.commit();
        }
    }

    public User findByUsername(String username) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            return session.getMapper(UserMapper.class).findByUsername(username);
        }
    }

    public int updatePassword(String id, String passwordHash, String salt) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            int updated = session.getMapper(UserMapper.class)
                    .updatePassword(id, passwordHash, salt);
            session.commit();
            return updated;
        }
    }
}
