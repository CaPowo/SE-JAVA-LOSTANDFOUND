package com.campus.lostfound.model;

/**
 * 失物类别。类别由独立数据表维护,失物通过关联表绑定类别。
 */
public class Category {

    private String id;
    private String name;
    private String createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return name;
    }
}
