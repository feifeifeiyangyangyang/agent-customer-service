package com.zhifutong.customer.util;

import java.nio.file.Path;

public final class FileNameUtil {
    private FileNameUtil() {
    }

    public static boolean isSafeOriginalName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String normalized = fileName.replace('\\', '/');
        if (normalized.contains("/") || normalized.contains("..")) {
            return false;
        }
        try {
            Path path = Path.of(fileName);
            return path.getFileName().toString().equals(fileName);
        } catch (Exception ex) {
            return false;
        }
    }

    public static String extensionOf(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }
}
