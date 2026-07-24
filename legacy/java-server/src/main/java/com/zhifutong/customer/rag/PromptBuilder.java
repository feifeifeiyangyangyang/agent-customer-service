package com.zhifutong.customer.rag;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    public static final String SYSTEM_PROMPT = """
            你是“智服通”的电商客服代表，不要说自己是 AI，也不要说“根据知识库/检索资料”。
            回答要像真人客服：先给结论，再补充条件、操作步骤和需要用户提供的信息。
            能直接回答时，控制在 2 到 5 句，语气自然、干净、专业，不要写论文式长段落。
            不确定时不要编造订单状态、退款到账时间、物流时效或售后结论；要说明需要用户提供商品名、订单号或转人工核实。
            如果资料只给了通用规则，允许先回答通用规则，再提醒“具体以订单页/人工核实为准”。
            不要使用“很抱歉，根据目前可检索到的信息”这类机械句式。
            不要使用 emoji。
            """;

    public String buildUserPrompt(String question, List<KnowledgeChunk> chunks) {
        String context = chunks.stream()
                .map(chunk -> "[来源: %s | 文档ID: %d | 片段: %d | 分数: %.2f]\n%s"
                        .formatted(chunk.fileName(), chunk.documentId(), chunk.chunkIndex(), chunk.score(), chunk.content()))
                .collect(Collectors.joining("\n\n---\n\n"));
        return """
                ## 可用业务资料
                %s

                ## 用户问题
                %s

                请按以下方式回答：
                1. 先直接回答用户最关心的问题。
                2. 如果需要区分商品、订单或售后条件，请用简短条目说明。
                3. 如果缺少关键条件，只问最必要的 1 到 2 个补充信息。
                4. 只使用上方业务资料中的事实，不要自行新增政策。
                """.formatted(context, question);
    }
}
