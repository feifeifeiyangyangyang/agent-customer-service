package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.TicketCategory;
import com.zhifutong.customer.domain.TicketStatus;
import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        Long userId,
        String ticketNo,
        Long conversationId,
        TicketCategory category,
        String description,
        String contact,
        TicketStatus status,
        Long handlerId,
        String priority,
        String handlingNote,
        String resolution,
        LocalDateTime resolvedAt,
        Integer lockVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
