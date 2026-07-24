package com.zhifutong.customer.controller;

import com.zhifutong.customer.application.ModelRuntimeConfigService;
import com.zhifutong.customer.dto.UpdateModelRuntimeConfigRequest;
import com.zhifutong.customer.vo.ApiResponse;
import com.zhifutong.customer.vo.ModelRuntimeConfigResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModelRuntimeConfigController {
    private final ModelRuntimeConfigService configService;

    public ModelRuntimeConfigController(ModelRuntimeConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/api/v1/admin/model-config")
    public ApiResponse<ModelRuntimeConfigResponse> get() {
        return ApiResponse.ok(configService.get());
    }

    @PutMapping("/api/v1/admin/model-config")
    public ApiResponse<ModelRuntimeConfigResponse> update(@Valid @RequestBody UpdateModelRuntimeConfigRequest request) {
        return ApiResponse.ok(configService.update(request.temperature(), request.topK(),
                request.minRetrievalScore(), request.mockEnabled()));
    }
}
