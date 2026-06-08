package com.campus.lostfound.model;

/**
 * 失物当前状态。
 */
public enum ItemStatus {
    PENDING("待认领"),
    CLAIMED("已认领"),
    TRANSFERRED("已移交");

    private final String label;

    ItemStatus(String label) {
        this.label = label;
    }

    /** 用于界面显示的中文名称。 */
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    /** 根据中文名称反查枚举,找不到时默认返回待认领。 */
    public static ItemStatus fromLabel(String label) {
        for (ItemStatus s : values()) {
            if (s.label.equals(label)) {
                return s;
            }
        }
        return PENDING;
    }
}
