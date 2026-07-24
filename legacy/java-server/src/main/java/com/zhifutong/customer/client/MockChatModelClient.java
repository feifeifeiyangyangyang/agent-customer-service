package com.zhifutong.customer.client;

public class MockChatModelClient implements ChatModelClient {
    @Override
    public String answer(String systemPrompt, String userPrompt) {
        return "可以的，我先按当前业务资料给你一个演示答复。真实大模型暂未启用，所以这条回复只用于编译、联调和自动化测试。";
    }
}
