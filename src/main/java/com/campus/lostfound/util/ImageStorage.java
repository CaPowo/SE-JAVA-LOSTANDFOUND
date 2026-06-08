package com.campus.lostfound.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * 失物图片存储工具。图片文件保存在 data/photos,数据库只保存相对路径。
 */
public final class ImageStorage {

    private static final Path PHOTO_DIR = Path.of("data", "photos");
    private static final String RELATIVE_PREFIX = "data/photos/";

    private ImageStorage() {
    }

    public static String copyForItem(String itemId, Path source) throws IOException {
        if (isBlank(itemId)) {
            throw new IllegalArgumentException("物品 ID 不能为空");
        }
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("请选择有效的图片文件");
        }

        Files.createDirectories(PHOTO_DIR);

        String fileName = itemId + extensionOf(source.getFileName().toString());
        Path target = PHOTO_DIR.resolve(fileName).normalize();
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (normalizedSource.equals(normalizedTarget)) {
            return RELATIVE_PREFIX + fileName;
        }

        deletePhotosForItem(itemId);
        Files.copy(normalizedSource, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
        return RELATIVE_PREFIX + fileName;
    }

    public static void deletePhotosForItem(String itemId) throws IOException {
        if (isBlank(itemId) || !Files.isDirectory(PHOTO_DIR)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PHOTO_DIR)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (name.equals(itemId) || name.startsWith(itemId + ".")) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    public static void deleteByRelativePath(String relativePath) throws IOException {
        if (isBlank(relativePath)) {
            return;
        }
        Path path = resolve(relativePath);
        Path photosRoot = PHOTO_DIR.toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(photosRoot)) {
            Files.deleteIfExists(absolute);
        }
    }

    public static Path resolve(String relativePath) {
        if (isBlank(relativePath)) {
            return null;
        }
        Path path = Path.of(relativePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of("").resolve(path).normalize();
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.length() > 8 || !ext.matches("\\.[a-z0-9]+")) {
            return "";
        }
        return ext;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
