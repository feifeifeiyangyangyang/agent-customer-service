package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNo,
        Long userId,
        ProductResponse product,
        Integer quantity,
        BigDecimal amount,
        OrderStatus status,
        LocalDateTime paidAt,
        LocalDateTime expectedShipAt,
        LocalDateTime shippedAt,
        LocalDateTime signedAt,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String remark,
        List<ShipmentEventResponse> shipmentEvents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
