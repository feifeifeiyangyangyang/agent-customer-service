package com.zhifutong.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        Long conversationId,
        @NotBlank String question
) {
}
