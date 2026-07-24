package com.zhifutong.customer.client;

import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.exception.BusinessException;
import java.util.function.DoubleSupplier;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

public class OpenAiCompatibleChatModelClient implements ChatModelClient {
    private final AppProperties properties;
    private final WebClient webClient;
    private final DoubleSupplier temperatureSupplier;

    public OpenAiCompatibleChatModelClient(AppProperties properties, WebClient.Builder builder) {
        this(properties, builder, () -> properties.getLlm().getTemperature());
    }

    public OpenAiCompatibleChatModelClient(AppProperties properties, WebClient.Builder builder, DoubleSupplier temperatureSupplier) {
        this.properties = properties;
        this.webClient = builder.baseUrl(properties.getLlm().getBaseUrl()).build();
        this.temperatureSupplier = temperatureSupplier;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String answer(String systemPrompt, String userPrompt) {
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank() || "replace_me".equals(apiKey)) {
            throw new BusinessException("首次真实调用大模型 API 前需要用户提供新的 LLM_API_KEY");
        }
        Map<String, Object> body = Map.of(
                "model", properties.getLlm().getModelName(),
                "temperature", temperatureSupplier.getAsDouble(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        Map<String, Object> response = webClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new BusinessException("大模型返回为空");
        }
        Object first = choices.get(0);
        if (first instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message) {
            Object content = message.get("content");
            if (content != null) {
                return content.toString();
            }
        }
        throw new BusinessException("大模型响应格式不符合预期");
    }
}
