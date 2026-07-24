package com.zhifutong.customer.application;

import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.entity.ModelRuntimeConfig;
import com.zhifutong.customer.mapper.ModelRuntimeConfigMapper;
import com.zhifutong.customer.vo.ModelRuntimeConfigResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelRuntimeConfigService {
    private static final long SINGLETON_ID = 1L;

    private final ModelRuntimeConfigMapper mapper;
    private final AppProperties properties;

    public ModelRuntimeConfigService(ModelRuntimeConfigMapper mapper, AppProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public ModelRuntimeConfig current() {
        ModelRuntimeConfig config = mapper.selectById(SINGLETON_ID);
        if (config != null) {
            return config;
        }
        ModelRuntimeConfig defaults = new ModelRuntimeConfig();
        defaults.setId(SINGLETON_ID);
        defaults.setTemperature(BigDecimal.valueOf(properties.getLlm().getTemperature()));
        defaults.setTopK(properties.getRag().getTopK());
        defaults.setMinRetrievalScore(BigDecimal.valueOf(properties.getRag().getMinRetrievalScore()));
        defaults.setMockEnabled(properties.getLlm().isMockEnabled() || properties.getLlm().getApiKey() == null || properties.getLlm().getApiKey().isBlank());
        defaults.setUpdatedAt(LocalDateTime.now());
        return defaults;
    }

    public int currentTopK() {
        return current().getTopK();
    }

    public double currentTemperature() {
        return current().getTemperature().doubleValue();
    }

    public double currentMinRetrievalScore() {
        return current().getMinRetrievalScore().doubleValue();
    }

    public boolean isMockEnabled() {
        return Boolean.TRUE.equals(current().getMockEnabled());
    }

    public ModelRuntimeConfigResponse get() {
        return toResponse(current());
    }

    @Transactional
    public ModelRuntimeConfigResponse update(BigDecimal temperature, Integer topK, BigDecimal minRetrievalScore, Boolean mockEnabled) {
        ModelRuntimeConfig config = new ModelRuntimeConfig();
        config.setId(SINGLETON_ID);
        config.setTemperature(temperature);
        config.setTopK(topK);
        config.setMinRetrievalScore(minRetrievalScore);
        config.setMockEnabled(mockEnabled);
        config.setUpdatedAt(LocalDateTime.now());
        if (mapper.selectById(SINGLETON_ID) == null) {
            mapper.insert(config);
        } else {
            mapper.updateById(config);
        }
        return toResponse(config);
    }

    private ModelRuntimeConfigResponse toResponse(ModelRuntimeConfig config) {
        return new ModelRuntimeConfigResponse(config.getTemperature(), config.getTopK(),
                config.getMinRetrievalScore(), config.getMockEnabled(), config.getUpdatedAt());
    }
}
