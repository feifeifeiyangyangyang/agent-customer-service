package com.zhifutong.customer.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhifutong.customer.auth.AuthContext;
import com.zhifutong.customer.auth.AuthenticatedUser;
import com.zhifutong.customer.client.ChatModelClient;
import com.zhifutong.customer.client.EmbeddingClient;
import com.zhifutong.customer.client.QdrantVectorStore;
import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.domain.ConfidenceLevel;
import com.zhifutong.customer.domain.MessageRole;
import com.zhifutong.customer.entity.ChatConversation;
import com.zhifutong.customer.entity.ChatMessage;
import com.zhifutong.customer.entity.ChatMessageSource;
import com.zhifutong.customer.entity.KbChunk;
import com.zhifutong.customer.exception.BusinessException;
import com.zhifutong.customer.mapper.ChatMessageSourceMapper;
import com.zhifutong.customer.mapper.KbChunkMapper;
import com.zhifutong.customer.rag.ConfidenceCalculator;
import com.zhifutong.customer.rag.KeywordKnowledgeSearch;
import com.zhifutong.customer.rag.KnowledgeChunk;
import com.zhifutong.customer.rag.PromptBuilder;
import com.zhifutong.customer.rag.SourceReference;
import com.zhifutong.customer.vo.ChatResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatApplicationService {
    private final ConversationService conversationService;
    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStore vectorStore;
    private final KeywordKnowledgeSearch keywordKnowledgeSearch;
    private final ChatModelClient chatModelClient;
    private final PromptBuilder promptBuilder;
    private final ConfidenceCalculator confidenceCalculator;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final ChatMessageSourceMapper messageSourceMapper;
    private final KbChunkMapper chunkMapper;
    private final CommerceApplicationService commerceService;
    private final ModelRuntimeConfigService modelRuntimeConfigService;

    public ChatApplicationService(ConversationService conversationService, EmbeddingClient embeddingClient,
                                  QdrantVectorStore vectorStore, ChatModelClient chatModelClient,
                                  KeywordKnowledgeSearch keywordKnowledgeSearch, PromptBuilder promptBuilder,
                                  ConfidenceCalculator confidenceCalculator,
                                  AppProperties properties, ObjectMapper objectMapper,
                                  ChatMessageSourceMapper messageSourceMapper, KbChunkMapper chunkMapper,
                                  CommerceApplicationService commerceService,
                                  ModelRuntimeConfigService modelRuntimeConfigService) {
        this.conversationService = conversationService;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.keywordKnowledgeSearch = keywordKnowledgeSearch;
        this.chatModelClient = chatModelClient;
        this.promptBuilder = promptBuilder;
        this.confidenceCalculator = confidenceCalculator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.messageSourceMapper = messageSourceMapper;
        this.chunkMapper = chunkMapper;
        this.commerceService = commerceService;
        this.modelRuntimeConfigService = modelRuntimeConfigService;
    }

    @Transactional
    public ChatResponse chat(Long conversationId, String question) {
        AuthenticatedUser user = AuthContext.require();
        ChatConversation conversation = conversationId == null
                ? null
                : conversationService.requireAccessible(conversationId, user);
        Long actualConversationId = conversation == null
                ? conversationService.create(firstTitle(question), user.userId()).id()
                : conversation.getId();
        conversationService.saveMessage(actualConversationId, MessageRole.USER, question);

        java.util.Optional<String> businessAnswer = commerceService.answerBusinessQuestion(user, question);
        if (businessAnswer.isPresent()) {
            ChatResponse response = new ChatResponse(actualConversationId, businessAnswer.get(), List.of(), 1.0, ConfidenceLevel.HIGH, false, null);
            persistAssistant(response, List.of());
            return response;
        }

        int topK = modelRuntimeConfigService.currentTopK();
        double minRetrievalScore = modelRuntimeConfigService.currentMinRetrievalScore();
        List<KnowledgeChunk> chunks = mergeChunks(
                keywordKnowledgeSearch.search(question, topK),
                vectorStore.search(
                embeddingClient.embed(question),
                topK,
                minRetrievalScore
                ),
                topK
        );
        if (chunks.isEmpty()) {
            ChatResponse response = refuse(actualConversationId,
                    "这个问题我这边还缺少可核实的业务资料。请补充商品名称或订单号，我可以继续帮你判断；如果涉及具体订单状态，建议转人工核实。");
            persistAssistant(response, List.of());
            return response;
        }

        double bestScore = chunks.get(0).score();
        ConfidenceLevel level = confidenceCalculator.calculate(bestScore);
        if (level == ConfidenceLevel.LOW) {
            ChatResponse response = refuse(actualConversationId,
                    "我没有找到足够相关的规则，不能直接给你下结论。请补充商品名称、订单号或问题截图，客服可以继续核实。");
            persistAssistant(response, List.of());
            return response;
        }

        String answer;
        try {
            answer = chatModelClient.answer(PromptBuilder.SYSTEM_PROMPT, promptBuilder.buildUserPrompt(question, chunks));
        } catch (Exception ex) {
            throw new BusinessException("大模型调用失败: " + ex.getMessage());
        }
        List<SourceReference> sources = chunks.stream()
                .map(chunk -> new SourceReference(chunk.documentId(), chunk.fileName(), snippet(chunk.content()), chunk.score()))
                .toList();
        ChatResponse response = new ChatResponse(actualConversationId, answer, sources, bestScore, level, false, null);
        persistAssistant(response, chunks);
        return response;
    }

    private ChatResponse refuse(Long conversationId, String answer) {
        return new ChatResponse(conversationId, answer, List.of(), 0.0, ConfidenceLevel.LOW, true, null);
    }

    private void persistAssistant(ChatResponse response, List<KnowledgeChunk> chunks) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(response.conversationId());
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(response.answer());
        message.setSourcesJson(toJson(response.sources()));
        message.setRetrievalScore(BigDecimal.valueOf(response.retrievalScore()));
        message.setConfidenceLevel(response.confidenceLevel());
        message.setNeedHuman(response.needHuman());
        message.setCreatedAt(LocalDateTime.now());
        conversationService.saveAssistantMessage(message);
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            KbChunk storedChunk = chunkMapper.selectOne(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getDocumentId, chunk.documentId())
                    .eq(KbChunk::getChunkIndex, chunk.chunkIndex())
                    .last("LIMIT 1"));
            ChatMessageSource source = new ChatMessageSource();
            source.setMessageId(message.getId());
            source.setDocumentId(chunk.documentId());
            source.setChunkId(storedChunk == null ? null : storedChunk.getId());
            source.setRankNo(i + 1);
            source.setRetrievalScore(BigDecimal.valueOf(chunk.score()));
            source.setSnippetSnapshot(snippet(chunk.content()));
            source.setCreatedAt(LocalDateTime.now());
            messageSourceMapper.insert(source);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 220 ? text : text.substring(0, 220);
    }

    private List<KnowledgeChunk> mergeChunks(List<KnowledgeChunk> keywordChunks, List<KnowledgeChunk> vectorChunks, int limit) {
        java.util.Map<String, KnowledgeChunk> merged = new java.util.LinkedHashMap<>();
        for (KnowledgeChunk chunk : keywordChunks) {
            merged.put(key(chunk), chunk);
        }
        for (KnowledgeChunk chunk : vectorChunks) {
            merged.putIfAbsent(key(chunk), chunk);
        }
        return merged.values().stream()
                .sorted(java.util.Comparator.comparingDouble(KnowledgeChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    private String key(KnowledgeChunk chunk) {
        return chunk.documentId() + ":" + chunk.chunkIndex();
    }

    private String firstTitle(String question) {
        String clean = question == null ? "匿名客服会话" : question.trim();
        return clean.length() <= 20 ? clean : clean.substring(0, 20);
    }
}
