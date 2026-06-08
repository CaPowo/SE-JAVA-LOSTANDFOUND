package com.campus.lostfound.util;

import com.campus.lostfound.model.ItemStatus;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MyBatis 会话工厂和启动建表逻辑。
 */
public final class MyBatisUtil {

    private static final String DB_URL = "jdbc:sqlite:lostfound.db";
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LEGACY_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static SqlSessionFactory sqlSessionFactory;

    private MyBatisUtil() {
    }

    public static synchronized void init() {
        if (sqlSessionFactory != null) {
            return;
        }
        ensureDatabase();
        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        } catch (IOException e) {
            throw new RuntimeException("MyBatis 配置加载失败:" + e.getMessage(), e);
        }
    }

    public static SqlSessionFactory getSqlSessionFactory() {
        if (sqlSessionFactory == null) {
            init();
        }
        return sqlSessionFactory;
    }

    private static void ensureDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                conn.setAutoCommit(false);
                try {
                    if (lostItemNeedsMigration(conn)) {
                        migrateLegacyLostItem(conn);
                    } else {
                        executeSchema(conn);
                    }
                    migrateCategoryLinks(conn);
                    conn.commit();
                } catch (SQLException | IOException e) {
                    conn.rollback();
                    throw e;
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite 驱动加载失败", e);
        } catch (SQLException | IOException e) {
            throw new RuntimeException("数据库初始化失败:" + e.getMessage(), e);
        }
    }

    private static boolean lostItemNeedsMigration(Connection conn) throws SQLException {
        if (!tableExists(conn, "lost_item")) {
            return false;
        }
        Map<String, String> columns = columnTypes(conn, "lost_item");
        String idType = columns.getOrDefault("id", "").toUpperCase(Locale.ROOT);
        return !idType.contains("TEXT")
                || !columns.containsKey("finder_contact")
                || !columns.containsKey("image_path")
                || !columns.containsKey("created_at")
                || !columns.containsKey("updated_at");
    }

    private static void migrateLegacyLostItem(Connection conn) throws SQLException, IOException {
        String suffix = LocalDateTime.now().format(LEGACY_FMT);
        String legacyLostItem = "lost_item_legacy_" + suffix;
        String legacyClaimRecord = "claim_record_legacy_" + suffix;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF");
            if (tableExists(conn, "claim_record")) {
                stmt.execute("ALTER TABLE claim_record RENAME TO " + quote(legacyClaimRecord));
            }
            stmt.execute("ALTER TABLE lost_item RENAME TO " + quote(legacyLostItem));
        }

        executeSchema(conn);
        migrateLostItemRows(conn, legacyLostItem);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
    }

    private static void migrateLostItemRows(Connection conn, String legacyTable) throws SQLException {
        Set<String> columns = columnNames(conn, legacyTable);
        String selectSql = "SELECT * FROM " + quote(legacyTable) + " ORDER BY id";
        String insertItemSql = """
                INSERT INTO lost_item (
                    id, name, category, location, found_time, finder_contact, description,
                    image_path, status, claimer, claim_time, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String insertClaimSql = """
                INSERT INTO claim_record (id, item_id, claimer, contact, claim_time, operator)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Statement select = conn.createStatement();
             ResultSet rs = select.executeQuery(selectSql);
             PreparedStatement insertItem = conn.prepareStatement(insertItemSql);
             PreparedStatement insertClaim = conn.prepareStatement(insertClaimSql)) {
            while (rs.next()) {
                String newId = normalizedUuid(read(rs, columns, "id"));
                String now = LocalDateTime.now().format(TIME_FMT);
                String finderContact = columns.contains("finder_contact")
                        ? read(rs, columns, "finder_contact")
                        : read(rs, columns, "finder");
                String status = normalizeStatus(read(rs, columns, "status"));
                String claimTime = read(rs, columns, "claim_time");
                String claimer = read(rs, columns, "claimer");

                insertItem.setString(1, newId);
                insertItem.setString(2, read(rs, columns, "name"));
                insertItem.setString(3, read(rs, columns, "category"));
                insertItem.setString(4, read(rs, columns, "location"));
                insertItem.setString(5, read(rs, columns, "found_time"));
                insertItem.setString(6, finderContact);
                insertItem.setString(7, read(rs, columns, "description"));
                insertItem.setString(8, read(rs, columns, "image_path"));
                insertItem.setString(9, status);
                insertItem.setString(10, claimer);
                insertItem.setString(11, claimTime);
                insertItem.setString(12, now);
                insertItem.setString(13, now);
                insertItem.executeUpdate();

                if (!isBlank(claimer)) {
                    insertClaim.setString(1, UUID.randomUUID().toString());
                    insertClaim.setString(2, newId);
                    insertClaim.setString(3, claimer);
                    insertClaim.setString(4, "");
                    insertClaim.setString(5, isBlank(claimTime) ? now : claimTime);
                    insertClaim.setString(6, "系统迁移");
                    insertClaim.executeUpdate();
                }
            }
        }
    }

    private static void migrateCategoryLinks(Connection conn) throws SQLException {
        if (!tableExists(conn, "lost_item")
                || !tableExists(conn, "category")
                || !tableExists(conn, "lost_item_category")
                || !columnNames(conn, "lost_item").contains("category")) {
            return;
        }

        String selectSql = """
                SELECT id, category
                FROM lost_item
                WHERE category IS NOT NULL AND TRIM(category) <> ''
                """;
        try (Statement select = conn.createStatement();
             ResultSet rs = select.executeQuery(selectSql)) {
            while (rs.next()) {
                String itemId = rs.getString("id");
                String categoryName = rs.getString("category");
                String categoryId = ensureCategory(conn, categoryName);
                linkCategory(conn, itemId, categoryId);
            }
        }
    }

    private static String ensureCategory(Connection conn, String name) throws SQLException {
        String trimmed = name == null ? "" : name.trim();
        String selectSql = "SELECT id FROM category WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, trimmed);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        }

        String id = UUID.randomUUID().toString();
        String insertSql = "INSERT INTO category (id, name, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, id);
            ps.setString(2, trimmed);
            ps.setString(3, LocalDateTime.now().format(TIME_FMT));
            ps.executeUpdate();
        }
        return id;
    }

    private static void linkCategory(Connection conn, String itemId, String categoryId)
            throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO lost_item_category (item_id, category_id)
                VALUES (?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, categoryId);
            ps.executeUpdate();
        }
    }

    private static void executeSchema(Connection conn) throws SQLException, IOException {
        String schema = readResource("schema.sql");
        try (Statement stmt = conn.createStatement()) {
            for (String sql : schema.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    private static String readResource(String name) throws IOException {
        try (InputStream input = MyBatisUtil.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("找不到资源文件:" + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Map<String, String> columnTypes(Connection conn, String tableName)
            throws SQLException {
        Map<String, String> columns = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + quote(tableName) + ")")) {
            while (rs.next()) {
                columns.put(rs.getString("name").toLowerCase(Locale.ROOT),
                        rs.getString("type") == null ? "" : rs.getString("type"));
            }
        }
        return columns;
    }

    private static Set<String> columnNames(Connection conn, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + quote(tableName) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private static String read(ResultSet rs, Set<String> columns, String column) throws SQLException {
        if (!columns.contains(column.toLowerCase(Locale.ROOT))) {
            return "";
        }
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    private static String normalizedUuid(String oldId) {
        if (!isBlank(oldId)) {
            try {
                return UUID.fromString(oldId).toString();
            } catch (IllegalArgumentException ignored) {
                // 老表可能是整数自增 ID,这里统一换成新 UUID。
            }
        }
        return UUID.randomUUID().toString();
    }

    private static String normalizeStatus(String value) {
        if (isBlank(value)) {
            return ItemStatus.PENDING.name();
        }
        try {
            return ItemStatus.valueOf(value).name();
        } catch (IllegalArgumentException ignored) {
            return ItemStatus.fromLabel(value).name();
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
