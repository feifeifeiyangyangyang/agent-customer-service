package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.ConfidenceLevel;
import com.zhifutong.customer.domain.MessageRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long conversationId,
        MessageRole role,
        String content,
        String sourcesJson,
        BigDecimal retrievalScore,
        ConfidenceLevel confidenceLevel,
        Boolean needHuman,
        LocalDateTime createdAt
) {
}
