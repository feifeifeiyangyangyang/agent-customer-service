package com.zhifutong.customer.controller;

import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.vo.ApiResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final AppProperties properties;

    public HealthController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean llmConfigured = properties.getLlm().getApiKey() != null && !properties.getLlm().getApiKey().isBlank();
        boolean embeddingFilesExist = Files.exists(Path.of(properties.getEmbedding().getModelPath()))
                && Files.exists(Path.of(properties.getEmbedding().getTokenizerPath()));
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "llmConfigured", llmConfigured,
                "llmMode", llmConfigured ? "REAL_API_READY" : "MOCK_CHATMODEL",
                "embeddingMockEnabled", properties.getEmbedding().isMockEnabled(),
                "embeddingFilesExist", embeddingFilesExist,
                "qdrantCollection", properties.getQdrant().getCollection()
        ));
    }
}
