package com.zhifutong.customer.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DocumentStatusTest {
    @Test
    void onlyFailedDocumentsCanRetry() {
        assertTrue(DocumentStatus.FAILED.canRetry());
        assertFalse(DocumentStatus.COMPLETED.canRetry());
    }
}
