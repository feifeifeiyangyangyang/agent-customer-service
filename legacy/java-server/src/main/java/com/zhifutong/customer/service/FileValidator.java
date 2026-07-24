package com.zhifutong.customer.service;

import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.exception.BusinessException;
import com.zhifutong.customer.util.FileNameUtil;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileValidator {
    private final AppProperties properties;

    public FileValidator(AppProperties properties) {
        this.properties = properties;
    }

    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (!FileNameUtil.isSafeOriginalName(originalName)) {
            throw new BusinessException("文件名不合法");
        }
        String ext = FileNameUtil.extensionOf(originalName);
        if (!properties.getDocument().getAllowedExtensions().contains(ext)) {
            throw new BusinessException("不支持的文件类型: " + ext);
        }
        long maxBytes = properties.getDocument().getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("文件超过大小限制");
        }
        String contentType = file.getContentType();
        if (contentType != null && !isMimeCompatible(ext, contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("文件 MIME 类型与扩展名不匹配");
        }
        return ext;
    }

    private boolean isMimeCompatible(String ext, String mime) {
        return switch (ext) {
            case "pdf" -> mime.contains("pdf") || mime.equals("application/octet-stream");
            case "docx" -> mime.contains("wordprocessingml") || mime.equals("application/octet-stream");
            case "txt" -> mime.startsWith("text/") || mime.equals("application/octet-stream");
            case "md" -> mime.startsWith("text/") || mime.equals("application/octet-stream");
            default -> false;
        };
    }
}
