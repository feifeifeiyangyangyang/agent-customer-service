package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.DocumentStatus;
import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String originalName,
        String fileType,
        Long fileSize,
        DocumentStatus status,
        Integer chunkCount,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
