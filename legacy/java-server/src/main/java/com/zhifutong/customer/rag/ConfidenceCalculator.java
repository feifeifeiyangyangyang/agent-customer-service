package com.zhifutong.customer.rag;

import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.domain.ConfidenceLevel;
import org.springframework.stereotype.Component;

@Component
public class ConfidenceCalculator {
    private final AppProperties properties;

    public ConfidenceCalculator(AppProperties properties) {
        this.properties = properties;
    }

    public ConfidenceLevel calculate(double score) {
        if (score >= properties.getRag().getHighConfidenceScore()) {
            return ConfidenceLevel.HIGH;
        }
        if (score >= properties.getRag().getMediumConfidenceScore()) {
            return ConfidenceLevel.MEDIUM;
        }
        return ConfidenceLevel.LOW;
    }
}
