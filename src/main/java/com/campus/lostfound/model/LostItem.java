package com.campus.lostfound.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * 失物信息实体。使用 JavaFX 属性,便于直接绑定到 TableView。
 */
public class LostItem {

    private final StringProperty id = new SimpleStringProperty(this, "id", "");
    private final StringProperty name = new SimpleStringProperty(this, "name", "");
    private final StringProperty category = new SimpleStringProperty(this, "category", "");
    private final StringProperty location = new SimpleStringProperty(this, "location", "");
    private final StringProperty foundTime = new SimpleStringProperty(this, "foundTime", "");
    private final StringProperty finderContact = new SimpleStringProperty(this, "finderContact", "");
    private final StringProperty description = new SimpleStringProperty(this, "description", "");
    private final StringProperty imagePath = new SimpleStringProperty(this, "imagePath", "");
    private final ObjectProperty<ItemStatus> status =
            new SimpleObjectProperty<>(this, "status", ItemStatus.PENDING);
    private final StringProperty claimer = new SimpleStringProperty(this, "claimer", "");
    private final StringProperty claimTime = new SimpleStringProperty(this, "claimTime", "");
    private final StringProperty createdAt = new SimpleStringProperty(this, "createdAt", "");
    private final StringProperty updatedAt = new SimpleStringProperty(this, "updatedAt", "");

    public LostItem() {
    }

    // ---- id ----
    public String getId() { return id.get(); }
    public void setId(String value) { id.set(value); }
    public StringProperty idProperty() { return id; }

    // ---- 物品名称 ----
    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }
    public StringProperty nameProperty() { return name; }

    // ---- 类别 ----
    public String getCategory() { return category.get(); }
    public void setCategory(String value) { category.set(value); }
    public StringProperty categoryProperty() { return category; }

    // ---- 拾取地点 ----
    public String getLocation() { return location.get(); }
    public void setLocation(String value) { location.set(value); }
    public StringProperty locationProperty() { return location; }

    // ---- 拾取时间 ----
    public String getFoundTime() { return foundTime.get(); }
    public void setFoundTime(String value) { foundTime.set(value); }
    public StringProperty foundTimeProperty() { return foundTime; }

    // ---- 拾取人或联系方式 ----
    public String getFinderContact() { return finderContact.get(); }
    public void setFinderContact(String value) { finderContact.set(value); }
    public StringProperty finderContactProperty() { return finderContact; }

    public String getFinder() { return getFinderContact(); }
    public void setFinder(String value) { setFinderContact(value); }
    public StringProperty finderProperty() { return finderContactProperty(); }

    // ---- 物品描述 ----
    public String getDescription() { return description.get(); }
    public void setDescription(String value) { description.set(value); }
    public StringProperty descriptionProperty() { return description; }

    // ---- 图片相对路径 ----
    public String getImagePath() { return imagePath.get(); }
    public void setImagePath(String value) { imagePath.set(value); }
    public StringProperty imagePathProperty() { return imagePath; }

    // ---- 当前状态 ----
    public ItemStatus getStatus() { return status.get(); }
    public void setStatus(ItemStatus value) { status.set(value); }
    public ObjectProperty<ItemStatus> statusProperty() { return status; }

    // ---- 认领人 ----
    public String getClaimer() { return claimer.get(); }
    public void setClaimer(String value) { claimer.set(value); }
    public StringProperty claimerProperty() { return claimer; }

    // ---- 认领时间 ----
    public String getClaimTime() { return claimTime.get(); }
    public void setClaimTime(String value) { claimTime.set(value); }
    public StringProperty claimTimeProperty() { return claimTime; }

    // ---- 创建时间 ----
    public String getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(String value) { createdAt.set(value); }
    public StringProperty createdAtProperty() { return createdAt; }

    // ---- 更新时间 ----
    public String getUpdatedAt() { return updatedAt.get(); }
    public void setUpdatedAt(String value) { updatedAt.set(value); }
    public StringProperty updatedAtProperty() { return updatedAt; }
}
