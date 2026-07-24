package com.zhifutong.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhifutong.customer.auth.AuthenticatedUser;
import com.zhifutong.customer.domain.ConversationStatus;
import com.zhifutong.customer.domain.MessageRole;
import com.zhifutong.customer.entity.ChatConversation;
import com.zhifutong.customer.entity.ChatMessage;
import com.zhifutong.customer.exception.BusinessException;
import com.zhifutong.customer.mapper.ChatConversationMapper;
import com.zhifutong.customer.mapper.ChatMessageMapper;
import com.zhifutong.customer.vo.ConversationResponse;
import com.zhifutong.customer.vo.MessageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;

    public ConversationService(ChatConversationMapper conversationMapper, ChatMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    public ConversationResponse create(String title, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setConversationNo("C" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        conversation.setTitle(title == null || title.isBlank() ? "客服会话" : title.trim());
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return toResponse(conversation);
    }

    public ChatConversation require(Long id) {
        ChatConversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }
        return conversation;
    }

    public ChatConversation requireAccessible(Long id, AuthenticatedUser user) {
        ChatConversation conversation = require(id);
        if (!user.isAdmin() && conversation.getUserId() != null && !conversation.getUserId().equals(user.userId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能访问其他用户的会话");
        }
        return conversation;
    }

    public ConversationResponse get(Long id, AuthenticatedUser user) {
        return toResponse(requireAccessible(id, user));
    }

    public List<MessageResponse> messages(Long conversationId, AuthenticatedUser user) {
        requireAccessible(conversationId, user);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByAsc(ChatMessage::getCreatedAt))
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public void clearMessages(Long conversationId, AuthenticatedUser user) {
        ChatConversation conversation = requireAccessible(conversationId, user);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getConversationId, conversationId));
        conversation.setStatus(ConversationStatus.CLEARED);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    public ChatMessage saveMessage(Long conversationId, MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setNeedHuman(false);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    public void saveAssistantMessage(ChatMessage message) {
        messageMapper.insert(message);
    }

    public ConversationResponse toResponse(ChatConversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getConversationNo(), conversation.getTitle(),
                conversation.getStatus(), conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private MessageResponse toMessageResponse(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getConversationId(), message.getRole(), message.getContent(),
                message.getSourcesJson(), message.getRetrievalScore(), message.getConfidenceLevel(), message.getNeedHuman(),
                message.getCreatedAt());
    }
}
