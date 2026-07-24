package com.zhifutong.customer.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {
    @Test
    void splitKeepsChunksWithinConfiguredSize() {
        TextChunker chunker = new TextChunker();
        List<String> chunks = chunker.split("发货规则。".repeat(80), 60, 10, 5);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 70));
    }
}
