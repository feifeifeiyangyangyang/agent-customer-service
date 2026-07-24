package com.zhifutong.customer.controller;

import com.zhifutong.customer.application.ConversationService;
import com.zhifutong.customer.auth.AuthContext;
import com.zhifutong.customer.dto.CreateConversationRequest;
import com.zhifutong.customer.vo.ApiResponse;
import com.zhifutong.customer.vo.ConversationResponse;
import com.zhifutong.customer.vo.MessageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ApiResponse<ConversationResponse> create(@RequestBody(required = false) CreateConversationRequest request) {
        return ApiResponse.ok(conversationService.create(request == null ? null : request.title(), AuthContext.require().userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(conversationService.get(id, AuthContext.require()));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageResponse>> messages(@PathVariable Long id) {
        return ApiResponse.ok(conversationService.messages(id, AuthContext.require()));
    }

    @DeleteMapping("/{id}/messages")
    public ApiResponse<Void> clear(@PathVariable Long id) {
        conversationService.clearMessages(id, AuthContext.require());
        return ApiResponse.ok(null);
    }
}
