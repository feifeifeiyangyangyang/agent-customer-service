package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.ConversationStatus;
import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        String conversationNo,
        String title,
        ConversationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
