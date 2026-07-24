package com.zhifutong.customer.dto;

import com.zhifutong.customer.domain.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTicketRequest(
        @NotNull Long conversationId,
        @NotBlank String description,
        @NotNull TicketCategory category,
        String contact
) {
}
