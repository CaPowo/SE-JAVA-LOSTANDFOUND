package com.campus.lostfound.dao;

import com.campus.lostfound.mapper.CategoryMapper;
import com.campus.lostfound.mapper.ClaimRecordMapper;
import com.campus.lostfound.mapper.LostItemMapper;
import com.campus.lostfound.model.Category;
import com.campus.lostfound.model.ClaimRecord;
import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.LostItem;
import com.campus.lostfound.util.MyBatisUtil;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 失物表 CRUD,以及和类别、认领记录相关的事务性数据操作。
 */
public class LostItemDao {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<LostItem> findAll() {
        return query(null, null, null, null);
    }

    public List<LostItem> query(String keyword, String category, String location, ItemStatus status) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            LostItemMapper mapper = session.getMapper(LostItemMapper.class);
            return mapper.query(keyword, category, location, status);
        }
    }

    public LostItem findById(String id) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            return session.getMapper(LostItemMapper.class).findById(id);
        }
    }

    public void insert(LostItem item) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            LostItemMapper itemMapper = session.getMapper(LostItemMapper.class);
            CategoryMapper categoryMapper = session.getMapper(CategoryMapper.class);
            resolveCategory(categoryMapper, item);
            itemMapper.insert(item);
            linkCategory(categoryMapper, item);
            session.commit();
        }
    }

    public void update(LostItem item) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            LostItemMapper itemMapper = session.getMapper(LostItemMapper.class);
            CategoryMapper categoryMapper = session.getMapper(CategoryMapper.class);
            resolveCategory(categoryMapper, item);
            itemMapper.update(item);
            linkCategory(categoryMapper, item);
            session.commit();
        }
    }

    public LostItem delete(String id) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            LostItemMapper itemMapper = session.getMapper(LostItemMapper.class);
            CategoryMapper categoryMapper = session.getMapper(CategoryMapper.class);
            ClaimRecordMapper claimMapper = session.getMapper(ClaimRecordMapper.class);
            LostItem old = itemMapper.findById(id);
            if (old == null) {
                throw new IllegalArgumentException("要删除的失物记录不存在");
            }
            categoryMapper.unlinkItem(id);
            claimMapper.deleteByItemId(id);
            itemMapper.delete(id);
            session.commit();
            return old;
        }
    }

    public void claim(String itemId, ClaimRecord record) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            LostItemMapper itemMapper = session.getMapper(LostItemMapper.class);
            ClaimRecordMapper recordMapper = session.getMapper(ClaimRecordMapper.class);
            LostItem item = itemMapper.findById(itemId);
            if (item == null) {
                throw new IllegalArgumentException("要认领的失物记录不存在");
            }
            itemMapper.markClaimed(itemId, record.getClaimer(),
                    record.getClaimTime(), LocalDateTime.now().format(TIME_FMT));
            recordMapper.insert(record);
            session.commit();
        }
    }

    public List<ClaimRecord> findClaimHistory(String itemId) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            return session.getMapper(ClaimRecordMapper.class).findByItemId(itemId);
        }
    }

    private void resolveCategory(CategoryMapper mapper, LostItem item) {
        if (!isBlank(item.getCategoryId())) {
            Category category = mapper.findById(item.getCategoryId());
            if (category == null) {
                throw new IllegalArgumentException("选择的类别不存在");
            }
            item.setCategory(category.getName());
        } else if (!isBlank(item.getCategory())) {
            Category category = mapper.findByName(item.getCategory());
            if (category == null) {
                category = createCategoryInSession(mapper, item.getCategory());
            }
            item.setCategoryId(category.getId());
            item.setCategory(category.getName());
        }
    }

    private void linkCategory(CategoryMapper mapper, LostItem item) {
        mapper.unlinkItem(item.getId());
        if (!isBlank(item.getCategoryId())) {
            mapper.linkItemCategory(item.getId(), item.getCategoryId());
        }
    }

    private Category createCategoryInSession(CategoryMapper mapper, String name) {
        Category category = new Category();
        category.setId(UUID.randomUUID().toString());
        category.setName(name.trim());
        category.setCreatedAt(LocalDateTime.now().format(TIME_FMT));
        mapper.insert(category);
        return category;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
