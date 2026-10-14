package com.collegeassist.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_msg_conv_id", columnList = "conversationId"),
        @Index(name = "idx_msg_seq", columnList = "sequenceNumber")
})
public class ChatMessageRecord {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(length = 64, nullable = false)
    private String conversationId;

    @Column(nullable = false, length = 20)
    private String sender; // "user" or "assistant"

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 30)
    private String timestamp;

    @Column(length = 30)
    private String status;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String sourcesJson;

    @Column(nullable = false)
    private Integer sequenceNumber;

    public ChatMessageRecord() {
    }

    public ChatMessageRecord(String id, String conversationId, String sender, String content, String timestamp, String status, String sourcesJson, Integer sequenceNumber) {
        this.id = id;
        this.conversationId = conversationId;
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
        this.status = status;
        this.sourcesJson = sourcesJson;
        this.sequenceNumber = sequenceNumber;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourcesJson() {
        return sourcesJson;
    }

    public void setSourcesJson(String sourcesJson) {
        this.sourcesJson = sourcesJson;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
