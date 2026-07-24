package com.zhifutong.customer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(20) Integer quantity,
        @NotBlank @Size(max = 64) String receiverName,
        @NotBlank @Size(max = 32) String receiverPhone,
        @NotBlank @Size(max = 255) String receiverAddress,
        @Size(max = 255) String remark
) {
}
