package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.ProductSaleStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String productCode,
        String productName,
        String category,
        ProductSaleStatus saleStatus,
        BigDecimal price,
        Integer stockQuantity,
        String dispatchRule,
        String afterSaleRule,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
