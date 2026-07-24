package com.zhifutong.customer.dto;

import com.zhifutong.customer.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        String carrier,
        String trackingNo,
        String location,
        String eventNote
) {
}
