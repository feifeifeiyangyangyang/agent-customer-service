package com.zhifutong.customer.vo;

import com.zhifutong.customer.domain.ConfidenceLevel;
import com.zhifutong.customer.rag.SourceReference;
import java.util.List;

public record ChatResponse(
        Long conversationId,
        String answer,
        List<SourceReference> sources,
        double retrievalScore,
        ConfidenceLevel confidenceLevel,
        boolean needHuman,
        Long ticketId
) {
}
