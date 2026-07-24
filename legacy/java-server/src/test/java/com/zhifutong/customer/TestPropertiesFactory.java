package com.zhifutong.customer;

import com.zhifutong.customer.config.AppProperties;
import java.util.List;

public final class TestPropertiesFactory {
    private TestPropertiesFactory() {
    }

    public static AppProperties create() {
        AppProperties properties = new AppProperties();
        AppProperties.Document document = new AppProperties.Document();
        document.setAllowedExtensions(List.of("pdf", "docx", "txt", "md"));
        document.setMaxSizeMb(1);
        document.setStoragePath("./target/test-documents");
        properties.setDocument(document);

        AppProperties.Rag rag = new AppProperties.Rag();
        rag.setChunkSize(80);
        rag.setChunkOverlap(10);
        rag.setMinChunkLength(5);
        rag.setTopK(5);
        rag.setMinRetrievalScore(0.65);
        rag.setHighConfidenceScore(0.80);
        rag.setMediumConfidenceScore(0.65);
        properties.setRag(rag);

        AppProperties.Embedding embedding = new AppProperties.Embedding();
        embedding.setDimension(16);
        embedding.setMockEnabled(true);
        embedding.setModelPath("./missing.onnx");
        embedding.setTokenizerPath("./missing.json");
        properties.setEmbedding(embedding);

        AppProperties.Llm llm = new AppProperties.Llm();
        llm.setMockEnabled(true);
        llm.setApiKey("");
        llm.setBaseUrl("https://api.deepseek.com");
        llm.setModelName("deepseek-v4-flash");
        properties.setLlm(llm);

        AppProperties.Qdrant qdrant = new AppProperties.Qdrant();
        qdrant.setHost("localhost");
        qdrant.setPort(6333);
        qdrant.setCollection("test");
        properties.setQdrant(qdrant);

        AppProperties.Auth auth = new AppProperties.Auth();
        auth.setJwtSecret("test-secret-change-me-at-least-32-chars");
        auth.setAccessTokenTtlMinutes(30);
        auth.setRefreshTokenTtlDays(7);
        properties.setAuth(auth);
        return properties;
    }
}
