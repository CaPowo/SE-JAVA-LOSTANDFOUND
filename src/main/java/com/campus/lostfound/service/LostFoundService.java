package com.campus.lostfound.service;

import com.campus.lostfound.dao.CategoryDao;
import com.campus.lostfound.dao.LostItemDao;
import com.campus.lostfound.model.Category;
import com.campus.lostfound.model.ClaimRecord;
import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.LostItem;
import com.campus.lostfound.util.ImageStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 失物业务服务。这里负责业务规则,CRUD 交给 DAO 层。
 */
public class LostFoundService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LostItemDao lostItemDao = new LostItemDao();
    private final CategoryDao categoryDao = new CategoryDao();

    public List<LostItem> findAll() {
        return lostItemDao.findAll();
    }

    public List<Category> findCategories() {
        return categoryDao.findAll();
    }

    public Category createCategory(String name) {
        String trimmed = trimToEmpty(name);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("类别名称不能为空");
        }

        Category category = new Category();
        category.setId(UUID.randomUUID().toString());
        category.setName(trimmed);
        category.setCreatedAt(now());
        return categoryDao.insertIfAbsent(category);
    }

    public void renameCategory(String id, String name) {
        String trimmed = trimToEmpty(name);
        if (isBlank(id)) {
            throw new IllegalArgumentException("请先选择类别");
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("类别名称不能为空");
        }
        Category existing = categoryDao.findByName(trimmed);
        if (existing != null && !id.equals(existing.getId())) {
            throw new IllegalArgumentException("该类别名称已存在");
        }
        categoryDao.updateName(id, trimmed);
    }

    public void deleteCategory(String id) {
        if (isBlank(id)) {
            throw new IllegalArgumentException("请先选择类别");
        }
        categoryDao.delete(id);
    }

    public List<LostItem> query(String keyword, String category, String location, ItemStatus status) {
        return lostItemDao.query(
                trimToNull(keyword),
                trimToNull(category),
                trimToNull(location),
                status);
    }

    public void create(LostItem item, Path imageSource) {
        boolean copiedImage = false;
        try {
            prepareNewItem(item);
            if (imageSource != null) {
                item.setImagePath(ImageStorage.copyForItem(item.getId(), imageSource));
                copiedImage = true;
            }
            lostItemDao.insert(item);
        } catch (RuntimeException | IOException e) {
            if (copiedImage) {
                deletePhotosQuietly(item.getId());
            }
            throw new RuntimeException("新增失物失败:" + e.getMessage(), e);
        }
    }

    public void update(LostItem item, Path imageSource) {
        boolean deleteOldImageAfterCommit = false;
        String oldImagePath = "";
        try {
            LostItem old = lostItemDao.findById(item.getId());
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

            lostItemDao.update(item);
            if (deleteOldImageAfterCommit) {
                ImageStorage.deleteByRelativePath(oldImagePath);
            }
        } catch (RuntimeException | IOException e) {
            throw new RuntimeException("修改失物失败:" + e.getMessage(), e);
        }
    }

    public void delete(String id) {
        try {
            LostItem old = lostItemDao.delete(id);
            deletePhotosQuietly(id);
            ImageStorage.deleteByRelativePath(old.getImagePath());
        } catch (RuntimeException | IOException e) {
            throw new RuntimeException("删除失物失败:" + e.getMessage(), e);
        }
    }

    public void claim(String itemId, String claimer, String contact, String operator) {
        if (isBlank(claimer)) {
            throw new IllegalArgumentException("认领人不能为空");
        }

        String claimTime = now();
        ClaimRecord record = new ClaimRecord();
        record.setId(UUID.randomUUID().toString());
        record.setItemId(itemId);
        record.setClaimer(claimer.trim());
        record.setContact(trimToEmpty(contact));
        record.setClaimTime(claimTime);
        record.setOperator(trimToEmpty(operator));
        lostItemDao.claim(itemId, record);
    }

    public List<ClaimRecord> findClaimHistory(String itemId) {
        return lostItemDao.findClaimHistory(itemId);
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
