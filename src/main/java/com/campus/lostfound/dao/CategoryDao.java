package com.campus.lostfound.dao;

import com.campus.lostfound.mapper.CategoryMapper;
import com.campus.lostfound.model.Category;
import com.campus.lostfound.util.MyBatisUtil;
import org.apache.ibatis.session.SqlSession;

import java.util.List;

/**
 * 类别表 CRUD。
 */
public class CategoryDao {

    public List<Category> findAll() {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            return session.getMapper(CategoryMapper.class).findAll();
        }
    }

    public Category findByName(String name) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            return session.getMapper(CategoryMapper.class).findByName(name);
        }
    }

    public Category insertIfAbsent(Category category) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            CategoryMapper mapper = session.getMapper(CategoryMapper.class);
            Category existing = mapper.findByName(category.getName());
            if (existing != null) {
                return existing;
            }
            mapper.insert(category);
            session.commit();
            return category;
        }
    }

    public void updateName(String id, String name) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            int updated = session.getMapper(CategoryMapper.class).updateName(id, name);
            if (updated != 1) {
                throw new IllegalArgumentException("类别不存在");
            }
            session.commit();
        }
    }

    public void delete(String id) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            CategoryMapper mapper = session.getMapper(CategoryMapper.class);
            if (mapper.countItemLinks(id) > 0) {
                throw new IllegalArgumentException("该类别已被失物使用,不能删除。");
            }
            int deleted = mapper.delete(id);
            if (deleted != 1) {
                throw new IllegalArgumentException("类别不存在");
            }
            session.commit();
        }
    }

    public int countItemLinks(String id) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            return session.getMapper(CategoryMapper.class).countItemLinks(id);
        }
    }
}
