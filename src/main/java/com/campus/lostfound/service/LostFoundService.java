package com.campus.lostfound.service;

import com.campus.lostfound.mapper.ClaimRecordMapper;
import com.campus.lostfound.mapper.CategoryMapper;
import com.campus.lostfound.mapper.LostItemMapper;
import com.campus.lostfound.model.Category;
import com.campus.lostfound.model.ClaimRecord;
import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.LostItem;
import com.campus.lostfound.util.ImageStorage;
import com.campus.lostfound.util.MyBatisUtil;
import org.apache.ibatis.session.SqlSession;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 失物、图片和认领历史的业务服务。
 */
public class LostFoundService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<LostItem> findAll() {
        return query(null, null, null, null);
    }

    public List<Category> findCategories() {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            CategoryMapper mapper = session.getMapper(CategoryMapper.class);
            return mapper.findAll();
        }
    }

    public Category createCategory(String name) {
        String trimmed = trimToEmpty(name);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("类别名称不能为空");
        }
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false)) {
            CategoryMapper mapper = session.getMapper(CategoryMapper.class);
            Category existing = mapper.findByName(trimmed);
            if (existing != null) {
                return existing;
            }

            Category category = new Category();
            category.setId(UUID.randomUUID().toString());
            category.setName(trimmed);
            category.setCreatedAt(now());
            mapper.insert(category);
            session.commit();
            return category;
        } catch (RuntimeException e) {
            throw new RuntimeException("新增类别失败:" + e.getMessage(), e);
        }
    }

    public List<LostItem> query(String keyword, String category, String location, ItemStatus status) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            LostItemMapper mapper = session.getMapper(LostItemMapper.class);
            return mapper.query(trimToNull(keyword), trimToNull(category), trimToNull(location), status);
        }
    }

    public void create(LostItem item, Path imageSource) {
        SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false);
        boolean copiedImage = false;
        try {
            LostItemMapper mapper = session.getMapper(LostItemMapper.class);
            CategoryMapper categoryMapper = session.getMapper(CategoryMapper.class);
            prepareNewItem(item);
            if (imageSource != null) {
                item.setImagePath(ImageStorage.copyForItem(item.getId(), imageSource));
                copiedImage = true;
            }
            mapper.insert(item);
            saveCategoryLink(categoryMapper, item);
            session.commit();
        } catch (RuntimeException | IOException e) {
            session.rollback();
            if (copiedImage) {
                deletePhotosQuietly(item.getId());
            }
            throw new RuntimeException("新增失物失败:" + e.getMessage(), e);
        } finally {
            session.close();
        }
    }

    public void update(LostItem item, Path imageSource) {
        SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false);
        boolean deleteOldImageAfterCommit = false;
        String oldImagePath = "";
        try {
            LostItemMapper mapper = session.getMapper(LostItemMapper.class);
            CategoryMapper categoryMapper = session.getMapper(CategoryMapper.class);
            LostItem old = mapper.findById(item.getId());
            if (old == null) {
                throw new IllegalArgumentException("要修改的失物记录不存在");
            }
            oldImagePath = old.getImagePath();
            sanitize(item);
            item.setCreatedAt(old.getCreatedAt());
            item.setUpdatedAt(now());
            if (imageSource != null) {
                item.setImagePath(ImageStorage.copyForItem(item.getId(), imageSource));
            } else if (isBlank(item.getImagePath()) && !isBlank(oldImagePath)) {
                deleteOldImageAfterCommit = true;
            }
            mapper.update(item);
            saveCategoryLink(categoryMapper, item);
            session.commit();
            if (deleteOldImageAfterCommit) {
                ImageStorage.deleteByRelativePath(oldImagePath);
            }
        } catch (RuntimeException | IOException e) {
            session.rollback();
            throw new RuntimeException("修改失物失败:" + e.getMessage(), e);
        } finally {
            session.close();
        }
    }

    public void delete(String id) {
        SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false);
        try {
            LostItemMapper itemMapper = session.getMapper(LostItemMapper.class);
            ClaimRecordMapper claimMapper = session.getMapper(ClaimRecordMapper.class);
            CategoryMapper categoryMapper = session.getMapper(CategoryMapper.class);
            LostItem old = itemMapper.findById(id);
            if (old == null) {
                throw new IllegalArgumentException("要删除的失物记录不存在");
            }
            categoryMapper.unlinkItem(id);
            claimMapper.deleteByItemId(id);
            itemMapper.delete(id);
            session.commit();
            deletePhotosQuietly(id);
            ImageStorage.deleteByRelativePath(old.getImagePath());
        } catch (RuntimeException | IOException e) {
            session.rollback();
            throw new RuntimeException("删除失物失败:" + e.getMessage(), e);
        } finally {
            session.close();
        }
    }

    public void claim(String itemId, String claimer, String contact, String operator) {
        if (isBlank(claimer)) {
            throw new IllegalArgumentException("认领人不能为空");
        }
        SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession(false);
        try {
            LostItemMapper itemMapper = session.getMapper(LostItemMapper.class);
            ClaimRecordMapper recordMapper = session.getMapper(ClaimRecordMapper.class);
            LostItem item = itemMapper.findById(itemId);
            if (item == null) {
                throw new IllegalArgumentException("要认领的失物记录不存在");
            }
            String claimTime = now();
            itemMapper.markClaimed(itemId, claimer.trim(), trimToEmpty(claimTime), claimTime);

            ClaimRecord record = new ClaimRecord();
            record.setId(UUID.randomUUID().toString());
            record.setItemId(itemId);
            record.setClaimer(claimer.trim());
            record.setContact(trimToEmpty(contact));
            record.setClaimTime(claimTime);
            record.setOperator(trimToEmpty(operator));
            recordMapper.insert(record);
            session.commit();
        } catch (RuntimeException e) {
            session.rollback();
            throw new RuntimeException("认领登记失败:" + e.getMessage(), e);
        } finally {
            session.close();
        }
    }

    public List<ClaimRecord> findClaimHistory(String itemId) {
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            ClaimRecordMapper mapper = session.getMapper(ClaimRecordMapper.class);
            return mapper.findByItemId(itemId);
        }
    }

    private void prepareNewItem(LostItem item) {
        if (isBlank(item.getId())) {
            item.setId(UUID.randomUUID().toString());
        }
        String time = now();
        sanitize(item);
        item.setCreatedAt(time);
        item.setUpdatedAt(time);
    }

    private void sanitize(LostItem item) {
        item.setName(trimToEmpty(item.getName()));
        item.setCategoryId(trimToEmpty(item.getCategoryId()));
        item.setCategory(trimToEmpty(item.getCategory()));
        item.setLocation(trimToEmpty(item.getLocation()));
        item.setFoundTime(trimToEmpty(item.getFoundTime()));
        item.setFinderContact(trimToEmpty(item.getFinderContact()));
        item.setDescription(trimToEmpty(item.getDescription()));
        item.setImagePath(trimToEmpty(item.getImagePath()));
        item.setClaimer(trimToEmpty(item.getClaimer()));
        item.setClaimTime(trimToEmpty(item.getClaimTime()));
        if (item.getStatus() == null) {
            item.setStatus(ItemStatus.PENDING);
        }
    }

    private void saveCategoryLink(CategoryMapper mapper, LostItem item) {
        mapper.unlinkItem(item.getId());
        if (!isBlank(item.getCategoryId())) {
            Category category = mapper.findById(item.getCategoryId());
            if (category == null) {
                throw new IllegalArgumentException("选择的类别不存在");
            }
            item.setCategory(category.getName());
            mapper.linkItemCategory(item.getId(), item.getCategoryId());
        } else if (!isBlank(item.getCategory())) {
            Category category = mapper.findByName(item.getCategory());
            if (category == null) {
                category = createCategoryInSession(mapper, item.getCategory());
            }
            item.setCategoryId(category.getId());
            item.setCategory(category.getName());
            mapper.linkItemCategory(item.getId(), category.getId());
        }
    }

    private Category createCategoryInSession(CategoryMapper mapper, String name) {
        Category category = new Category();
        category.setId(UUID.randomUUID().toString());
        category.setName(trimToEmpty(name));
        category.setCreatedAt(now());
        mapper.insert(category);
        return category;
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }

    private String trimToNull(String value) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void deletePhotosQuietly(String itemId) {
        try {
            ImageStorage.deletePhotosForItem(itemId);
        } catch (IOException ignored) {
            // 文件清理失败不影响数据库主流程。
        }
    }
}
