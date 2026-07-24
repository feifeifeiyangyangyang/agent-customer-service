package com.zhifutong.customer.client;

import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.rag.KnowledgeChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class QdrantVectorStore {
    private final AppProperties properties;
    private final WebClient webClient;

    public QdrantVectorStore(AppProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        String baseUrl = "http://%s:%d".formatted(properties.getQdrant().getHost(), properties.getQdrant().getPort());
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public void ensureCollection() {
        try {
            webClient.get()
                    .uri("/collections/{collection}", properties.getQdrant().getCollection())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return;
        } catch (Exception ignored) {
            // Create below when the collection does not exist yet.
        }
        Map<String, Object> body = Map.of(
                "vectors", Map.of(
                        "size", properties.getEmbedding().getDimension(),
                        "distance", "Cosine"
                )
        );
        webClient.put()
                .uri("/collections/{collection}", properties.getQdrant().getCollection())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void upsert(List<KnowledgeChunk> chunks, List<float[]> vectors) {
        ensureCollection();
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            points.add(Map.of(
                    "id", UUID.nameUUIDFromBytes("doc:%d:chunk:%d".formatted(chunk.documentId(), chunk.chunkIndex()).getBytes(StandardCharsets.UTF_8)).toString(),
                    "vector", toList(vectors.get(i)),
                    "payload", Map.of(
                            "documentId", chunk.documentId(),
                            "fileName", chunk.fileName(),
                            "chunkIndex", chunk.chunkIndex(),
                            "content", chunk.content()
                    )
            ));
        }
        webClient.put()
                .uri("/collections/{collection}/points?wait=true", properties.getQdrant().getCollection())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("points", points))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<KnowledgeChunk> search(float[] vector, int limit, double minScore) {
        ensureCollection();
        Map<String, Object> body = Map.of(
                "vector", toList(vector),
                "limit", limit,
                "with_payload", true
        );
        Map<String, Object> response = webClient.post()
                .uri("/collections/{collection}/points/search", properties.getQdrant().getCollection())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response == null || !(response.get("result") instanceof List<?> result)) {
            return List.of();
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (Object item : result) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Object scoreValue = row.get("score");
            double score = scoreValue instanceof Number number ? number.doubleValue() : 0.0;
            if (score < minScore || !(row.get("payload") instanceof Map<?, ?> payload)) {
                continue;
            }
            chunks.add(new KnowledgeChunk(
                    Long.valueOf(payload.get("documentId").toString()),
                    payload.get("fileName").toString(),
                    Integer.parseInt(payload.get("chunkIndex").toString()),
                    payload.get("content").toString(),
                    score
            ));
        }
        return chunks;
    }

    public void deleteByDocumentId(Long documentId) {
        ensureCollection();
        Map<String, Object> body = Map.of(
                "filter", Map.of(
                        "must", List.of(Map.of(
                                "key", "documentId",
                                "match", Map.of("value", documentId)
                        ))
                )
        );
        webClient.post()
                .uri("/collections/{collection}/points/delete?wait=true", properties.getQdrant().getCollection())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
