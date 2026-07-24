package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.ShipmentStatus;
import java.time.LocalDateTime;

public record ShipmentEventResponse(
        Long id,
        String carrier,
        String trackingNo,
        ShipmentStatus status,
        String location,
        String eventNote,
        LocalDateTime eventTime
) {
}
