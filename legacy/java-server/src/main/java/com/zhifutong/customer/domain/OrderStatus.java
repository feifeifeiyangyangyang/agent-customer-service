package com.zhifutong.customer.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    WAITING_SHIPMENT,
    SHIPPED,
    IN_TRANSIT,
    SIGNED,
    REFUNDING,
    REFUNDED,
    CANCELLED
}
