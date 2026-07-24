package com.zhifutong.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhifutong.customer.domain.ConversationStatus;
import java.time.LocalDateTime;

@TableName("chat_conversation")
public class ChatConversation {
    private Long id;
    private Long userId;
    private String conversationNo;
    private String title;
    private ConversationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationNo() { return conversationNo; }
    public void setConversationNo(String conversationNo) { this.conversationNo = conversationNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public ConversationStatus getStatus() { return status; }
    public void setStatus(ConversationStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
