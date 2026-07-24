package com.zhifutong.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhifutong.customer.domain.ConfidenceLevel;
import com.zhifutong.customer.domain.MessageRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("chat_message")
public class ChatMessage {
    private Long id;
    private Long conversationId;
    private MessageRole role;
    private String content;
    private String sourcesJson;
    private BigDecimal retrievalScore;
    private ConfidenceLevel confidenceLevel;
    private Boolean needHuman;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSourcesJson() { return sourcesJson; }
    public void setSourcesJson(String sourcesJson) { this.sourcesJson = sourcesJson; }
    public BigDecimal getRetrievalScore() { return retrievalScore; }
    public void setRetrievalScore(BigDecimal retrievalScore) { this.retrievalScore = retrievalScore; }
    public ConfidenceLevel getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(ConfidenceLevel confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    public Boolean getNeedHuman() { return needHuman; }
    public void setNeedHuman(Boolean needHuman) { this.needHuman = needHuman; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
