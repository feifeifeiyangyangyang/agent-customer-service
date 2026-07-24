package com.zhifutong.customer.config;

import com.zhifutong.customer.client.ChatModelClient;
import com.zhifutong.customer.client.ConfigurableChatModelClient;
import com.zhifutong.customer.client.DeterministicEmbeddingClient;
import com.zhifutong.customer.client.EmbeddingClient;
import com.zhifutong.customer.client.LocalOnnxEmbeddingClient;
import com.zhifutong.customer.application.ModelRuntimeConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ClientConfig {

    @Bean
    public EmbeddingClient embeddingClient(AppProperties properties) {
        if (properties.getEmbedding().isMockEnabled()) {
            return new DeterministicEmbeddingClient(properties);
        }
        return new LocalOnnxEmbeddingClient(properties);
    }

    @Bean
    public ChatModelClient chatModelClient(AppProperties properties, WebClient.Builder builder,
                                           ModelRuntimeConfigService configService) {
        return new ConfigurableChatModelClient(properties, builder, configService);
    }
}
