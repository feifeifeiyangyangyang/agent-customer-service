package com.zhifutong.customer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhifutong.customer.TestPropertiesFactory;
import com.zhifutong.customer.auth.AuthContext;
import com.zhifutong.customer.auth.AuthenticatedUser;
import com.zhifutong.customer.client.ChatModelClient;
import com.zhifutong.customer.client.EmbeddingClient;
import com.zhifutong.customer.client.QdrantVectorStore;
import com.zhifutong.customer.domain.ConfidenceLevel;
import com.zhifutong.customer.domain.UserRole;
import com.zhifutong.customer.exception.BusinessException;
import com.zhifutong.customer.mapper.ChatMessageSourceMapper;
import com.zhifutong.customer.mapper.KbChunkMapper;
import com.zhifutong.customer.rag.ConfidenceCalculator;
import com.zhifutong.customer.rag.KeywordKnowledgeSearch;
import com.zhifutong.customer.rag.KnowledgeChunk;
import com.zhifutong.customer.rag.PromptBuilder;
import com.zhifutong.customer.vo.ChatResponse;
import com.zhifutong.customer.vo.ConversationResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChatApplicationServiceTest {
    private ConversationService conversationService;
    private EmbeddingClient embeddingClient;
    private QdrantVectorStore vectorStore;
    private KeywordKnowledgeSearch keywordKnowledgeSearch;
    private ChatModelClient chatModelClient;
    private ChatMessageSourceMapper messageSourceMapper;
    private KbChunkMapper chunkMapper;
    private CommerceApplicationService commerceService;
    private ModelRuntimeConfigService modelRuntimeConfigService;
    private ChatApplicationService service;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        embeddingClient = mock(EmbeddingClient.class);
        vectorStore = mock(QdrantVectorStore.class);
        keywordKnowledgeSearch = mock(KeywordKnowledgeSearch.class);
        chatModelClient = mock(ChatModelClient.class);
        messageSourceMapper = mock(ChatMessageSourceMapper.class);
        chunkMapper = mock(KbChunkMapper.class);
        commerceService = mock(CommerceApplicationService.class);
        modelRuntimeConfigService = mock(ModelRuntimeConfigService.class);
        var properties = TestPropertiesFactory.create();
        service = new ChatApplicationService(
                conversationService,
                embeddingClient,
                vectorStore,
                chatModelClient,
                keywordKnowledgeSearch,
                new PromptBuilder(),
                new ConfidenceCalculator(properties),
                properties,
                new ObjectMapper(),
                messageSourceMapper,
                chunkMapper,
                commerceService,
                modelRuntimeConfigService
        );
        AuthContext.set(new AuthenticatedUser(1L, "user", "演示用户", UserRole.CUSTOMER, "test-token-id"));
        when(conversationService.create(anyString(), anyLong())).thenReturn(new ConversationResponse(1L, "C001", "匿名客服会话",
                null, LocalDateTime.now(), LocalDateTime.now()));
        doNothing().when(conversationService).saveAssistantMessage(any());
        when(embeddingClient.embed(any())).thenReturn(new float[] {0.1f, 0.2f});
        when(keywordKnowledgeSearch.search(any(), anyInt())).thenReturn(List.of());
        when(commerceService.answerBusinessQuestion(any(), anyString())).thenReturn(Optional.empty());
        when(modelRuntimeConfigService.currentTopK()).thenReturn(5);
        when(modelRuntimeConfigService.currentMinRetrievalScore()).thenReturn(0.42);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void reliableKnowledgeCallsChatModel() {
        when(vectorStore.search(any(), anyInt(), anyDouble())).thenReturn(List.of(
                new KnowledgeChunk(1L, "退换货政策.md", 0, "拆封不影响二次销售可申请退货。", 0.86)
        ));
        when(chatModelClient.answer(any(), any())).thenReturn("可以提交退货申请，但要保持配件齐全、包装完整。");

        ChatResponse response = service.chat(null, "拆封后能退货吗");

        assertEquals(ConfidenceLevel.HIGH, response.confidenceLevel());
        assertEquals(1, response.sources().size());
        verify(chatModelClient).answer(any(), any());
        verify(keywordKnowledgeSearch).search(any(), org.mockito.ArgumentMatchers.eq(5));
        verify(vectorStore).search(any(), org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.eq(0.42));
    }

    @Test
    void keywordFallbackCanCallChatModelWhenVectorSearchMisses() {
        when(vectorStore.search(any(), anyInt(), anyDouble())).thenReturn(List.of());
        when(keywordKnowledgeSearch.search(any(), anyInt())).thenReturn(List.of(
                new KnowledgeChunk(2L, "退款处理说明.md", 0, "仅退款审核通过后通常 1 到 3 个工作日原路退回。", 0.72)
        ));
        when(chatModelClient.answer(any(), any())).thenReturn("仅退款审核通过后通常 1 到 3 个工作日原路退回。");

        ChatResponse response = service.chat(null, "退款一般如何处理？");

        assertEquals(ConfidenceLevel.MEDIUM, response.confidenceLevel());
        assertEquals(1, response.sources().size());
        verify(chatModelClient).answer(any(), any());
    }

    @Test
    void noKnowledgeDoesNotCallChatModel() {
        when(vectorStore.search(any(), anyInt(), anyDouble())).thenReturn(List.of());

        ChatResponse response = service.chat(null, "能查真实订单吗");

        assertTrue(response.needHuman());
        verify(chatModelClient, never()).answer(any(), any());
    }

    @Test
    void businessAnswerBypassesRagAndChatModel() {
        when(commerceService.answerBusinessQuestion(any(), anyString())).thenReturn(Optional.of("订单预计今天 18:00 前发货。"));

        ChatResponse response = service.chat(null, "我的订单什么时候发货");

        assertEquals(ConfidenceLevel.HIGH, response.confidenceLevel());
        assertEquals("订单预计今天 18:00 前发货。", response.answer());
        verify(embeddingClient, never()).embed(any());
        verify(chatModelClient, never()).answer(any(), any());
    }

    @Test
    void lowRelatedResultReturnsHumanHandoff() {
        when(vectorStore.search(any(), anyInt(), anyDouble())).thenReturn(List.of(
                new KnowledgeChunk(1L, "账号问题.md", 0, "账号无法登录可重置密码。", 0.2)
        ));

        ChatResponse response = service.chat(null, "退款多久到账");

        assertTrue(response.needHuman());
        verify(chatModelClient, never()).answer(any(), any());
    }

    @Test
    void chatModelExceptionIsReported() {
        when(vectorStore.search(any(), anyInt(), anyDouble())).thenReturn(List.of(
                new KnowledgeChunk(1L, "退款处理说明.md", 0, "退款审核通过后原路退回。", 0.86)
        ));
        when(chatModelClient.answer(any(), any())).thenThrow(new BusinessException("API Key 缺失"));

        assertThrows(BusinessException.class, () -> service.chat(null, "退款怎么处理"));
    }
}
