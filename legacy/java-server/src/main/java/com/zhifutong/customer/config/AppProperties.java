package com.zhifutong.customer.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Document document = new Document();
    private Rag rag = new Rag();
    private Embedding embedding = new Embedding();
    private Qdrant qdrant = new Qdrant();
    private Llm llm = new Llm();
    private Auth auth = new Auth();
    private RateLimit rateLimit = new RateLimit();

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public Rag getRag() { return rag; }
    public void setRag(Rag rag) { this.rag = rag; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
    public Qdrant getQdrant() { return qdrant; }
    public void setQdrant(Qdrant qdrant) { this.qdrant = qdrant; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    public static class Document {
        private String storagePath;
        private long maxSizeMb;
        private List<String> allowedExtensions;

        public String getStoragePath() { return storagePath; }
        public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
        public long getMaxSizeMb() { return maxSizeMb; }
        public void setMaxSizeMb(long maxSizeMb) { this.maxSizeMb = maxSizeMb; }
        public List<String> getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(List<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
    }

    public static class Rag {
        private int chunkSize;
        private int chunkOverlap;
        private int minChunkLength;
        private int topK;
        private double minRetrievalScore;
        private double highConfidenceScore;
        private double mediumConfidenceScore;

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
        public int getMinChunkLength() { return minChunkLength; }
        public void setMinChunkLength(int minChunkLength) { this.minChunkLength = minChunkLength; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getMinRetrievalScore() { return minRetrievalScore; }
        public void setMinRetrievalScore(double minRetrievalScore) { this.minRetrievalScore = minRetrievalScore; }
        public double getHighConfidenceScore() { return highConfidenceScore; }
        public void setHighConfidenceScore(double highConfidenceScore) { this.highConfidenceScore = highConfidenceScore; }
        public double getMediumConfidenceScore() { return mediumConfidenceScore; }
        public void setMediumConfidenceScore(double mediumConfidenceScore) { this.mediumConfidenceScore = mediumConfidenceScore; }
    }

    public static class Embedding {
        private String modelPath;
        private String tokenizerPath;
        private int dimension;
        private int maxTokenLength = 256;
        private boolean mockEnabled;

        public String getModelPath() { return modelPath; }
        public void setModelPath(String modelPath) { this.modelPath = modelPath; }
        public String getTokenizerPath() { return tokenizerPath; }
        public void setTokenizerPath(String tokenizerPath) { this.tokenizerPath = tokenizerPath; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
        public int getMaxTokenLength() { return maxTokenLength; }
        public void setMaxTokenLength(int maxTokenLength) { this.maxTokenLength = maxTokenLength; }
        public boolean isMockEnabled() { return mockEnabled; }
        public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
    }

    public static class Qdrant {
        private String host;
        private int port;
        private String collection;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
    }

    public static class Llm {
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private boolean mockEnabled;
        private double temperature = 0.2;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public boolean isMockEnabled() { return mockEnabled; }
        public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
    }

    public static class Auth {
        private String jwtSecret;
        private long accessTokenTtlMinutes = 30;
        private long refreshTokenTtlDays = 7;

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        public long getAccessTokenTtlMinutes() { return accessTokenTtlMinutes; }
        public void setAccessTokenTtlMinutes(long accessTokenTtlMinutes) { this.accessTokenTtlMinutes = accessTokenTtlMinutes; }
        public long getRefreshTokenTtlDays() { return refreshTokenTtlDays; }
        public void setRefreshTokenTtlDays(long refreshTokenTtlDays) { this.refreshTokenTtlDays = refreshTokenTtlDays; }
    }

    public static class RateLimit {
        private int chatPerMinute = 20;

        public int getChatPerMinute() { return chatPerMinute; }
        public void setChatPerMinute(int chatPerMinute) { this.chatPerMinute = chatPerMinute; }
    }
}
