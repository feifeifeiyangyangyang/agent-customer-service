package com.zhifutong.customer.domain;

public enum DocumentStatus {
    PENDING,
    PROCESSING,
    READY,
    COMPLETED,
    FAILED;

    public boolean canRetry() {
        return this == FAILED;
    }
}
