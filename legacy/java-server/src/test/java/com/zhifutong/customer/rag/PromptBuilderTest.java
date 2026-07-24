package com.zhifutong.customer.rag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {
    @Test
    void promptContainsContextQuestionAndAnswerRules() {
        String prompt = new PromptBuilder().buildUserPrompt("能退货吗", List.of(
                new KnowledgeChunk(1L, "退换货政策.md", 0, "拆封后不影响二次销售可申请退货。", 0.9)
        ));

        assertTrue(prompt.contains("退换货政策.md"));
        assertTrue(prompt.contains("能退货吗"));
        assertTrue(prompt.contains("可用业务资料"));
        assertTrue(prompt.contains("先直接回答用户最关心的问题"));
        assertTrue(prompt.contains("不要自行新增政策"));
    }
}
