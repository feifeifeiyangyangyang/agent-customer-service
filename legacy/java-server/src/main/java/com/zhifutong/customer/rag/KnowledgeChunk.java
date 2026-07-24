package com.zhifutong.customer.rag;

public record KnowledgeChunk(
        Long documentId,
        String fileName,
        int chunkIndex,
        String content,
        double score
) {
}
