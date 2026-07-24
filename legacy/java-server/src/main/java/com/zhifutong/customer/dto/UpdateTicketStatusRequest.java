package com.zhifutong.customer.dto;

import com.zhifutong.customer.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(
        @NotNull TicketStatus status,
        String handlingNote,
        String resolution,
        Integer lockVersion
) {
}
