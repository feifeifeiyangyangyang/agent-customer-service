package com.zhifutong.customer.client;

public interface ChatModelClient {
    String answer(String systemPrompt, String userPrompt);
}
