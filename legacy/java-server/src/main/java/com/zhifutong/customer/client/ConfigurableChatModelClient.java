package com.zhifutong.customer.client;

import com.zhifutong.customer.application.ModelRuntimeConfigService;
import com.zhifutong.customer.config.AppProperties;
import org.springframework.web.reactive.function.client.WebClient;

public class ConfigurableChatModelClient implements ChatModelClient {
    private final AppProperties properties;
    private final ModelRuntimeConfigService configService;
    private final ChatModelClient mockClient;
    private final ChatModelClient realClient;

    public ConfigurableChatModelClient(AppProperties properties, WebClient.Builder builder,
                                       ModelRuntimeConfigService configService) {
        this.properties = properties;
        this.configService = configService;
        this.mockClient = new MockChatModelClient();
        this.realClient = new OpenAiCompatibleChatModelClient(properties, builder, configService::currentTemperature);
    }

    @Override
    public String answer(String systemPrompt, String userPrompt) {
        String apiKey = properties.getLlm().getApiKey();
        if (configService.isMockEnabled() || apiKey == null || apiKey.isBlank()) {
            return mockClient.answer(systemPrompt, userPrompt);
        }
        return realClient.answer(systemPrompt, userPrompt);
    }
}
