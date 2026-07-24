package com.zhifutong.customer.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zhifutong.customer.TestPropertiesFactory;
import com.zhifutong.customer.domain.ConfidenceLevel;
import org.junit.jupiter.api.Test;

class ConfidenceCalculatorTest {
    @Test
    void calculatesConfiguredLevels() {
        ConfidenceCalculator calculator = new ConfidenceCalculator(TestPropertiesFactory.create());
        assertEquals(ConfidenceLevel.HIGH, calculator.calculate(0.9));
        assertEquals(ConfidenceLevel.MEDIUM, calculator.calculate(0.7));
        assertEquals(ConfidenceLevel.LOW, calculator.calculate(0.4));
    }
}
