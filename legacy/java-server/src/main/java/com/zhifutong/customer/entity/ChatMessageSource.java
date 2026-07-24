package com.zhifutong.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("chat_message_source")
public class ChatMessageSource {
    private Long id;
    private Long messageId;
    private Long documentId;
    private Long chunkId;
    private Integer rankNo;
    private BigDecimal retrievalScore;
    private String snippetSnapshot;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public BigDecimal getRetrievalScore() { return retrievalScore; }
    public void setRetrievalScore(BigDecimal retrievalScore) { this.retrievalScore = retrievalScore; }
    public String getSnippetSnapshot() { return snippetSnapshot; }
    public void setSnippetSnapshot(String snippetSnapshot) { this.snippetSnapshot = snippetSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
