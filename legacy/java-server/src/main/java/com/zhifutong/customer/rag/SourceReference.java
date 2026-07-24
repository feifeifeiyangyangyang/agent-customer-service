package com.zhifutong.customer.rag;

public record SourceReference(
        Long documentId,
        String fileName,
        String snippet,
        double score
) {
}
