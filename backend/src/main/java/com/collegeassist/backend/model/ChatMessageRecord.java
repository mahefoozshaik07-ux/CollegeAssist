package com.collegeassist.backend.model;

public class ChatMessageRecord {

    private String id;
    private String conversationId;
    private String sender; // "user" or "assistant"
    private String content;
    private String timestamp;
    private String status;
    private String sourcesJson;
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
