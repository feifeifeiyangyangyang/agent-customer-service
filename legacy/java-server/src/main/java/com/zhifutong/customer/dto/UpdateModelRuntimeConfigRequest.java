package com.zhifutong.customer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateModelRuntimeConfigRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal temperature,
        @NotNull @Min(1) @Max(20) Integer topK,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal minRetrievalScore,
        @NotNull Boolean mockEnabled
) {
}
