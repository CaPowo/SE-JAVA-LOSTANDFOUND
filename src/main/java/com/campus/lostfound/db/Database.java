package com.campus.lostfound.db;

import com.campus.lostfound.util.MyBatisUtil;

/**
 * 数据库初始化门面。实际连接和 SQL 映射由 MyBatisUtil 管理。
 */
public final class Database {

    private Database() {
    }

    /** 程序启动时调用:初始化表结构和 MyBatis 会话工厂。 */
    public static void init() {
        MyBatisUtil.init();
    }
}
