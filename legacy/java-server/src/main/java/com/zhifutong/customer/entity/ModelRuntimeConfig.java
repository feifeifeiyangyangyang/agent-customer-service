package com.zhifutong.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("model_runtime_config")
public class ModelRuntimeConfig {
    private Long id;
    private BigDecimal temperature;
    private Integer topK;
    private BigDecimal minRetrievalScore;
    private Boolean mockEnabled;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public BigDecimal getMinRetrievalScore() { return minRetrievalScore; }
    public void setMinRetrievalScore(BigDecimal minRetrievalScore) { this.minRetrievalScore = minRetrievalScore; }
    public Boolean getMockEnabled() { return mockEnabled; }
    public void setMockEnabled(Boolean mockEnabled) { this.mockEnabled = mockEnabled; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
