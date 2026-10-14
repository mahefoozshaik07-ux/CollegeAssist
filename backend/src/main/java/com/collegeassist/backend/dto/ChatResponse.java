package com.collegeassist.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class ChatResponse {

    private String id;
    private String role;
    private String content;
    private String status;
    private String timestamp;
    private boolean configured;
    private List<SourceReference> sources = new ArrayList<>();

    public ChatResponse() {
        this.role = "assistant";
    }

    public ChatResponse(String id, String content, String status, String timestamp, boolean configured, List<SourceReference> sources) {
        this.id = id;
        this.role = "assistant";
        this.content = content;
        this.status = status;
        this.timestamp = timestamp;
        this.configured = configured;
        this.sources = (sources != null) ? sources : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public List<SourceReference> getSources() {
        return sources;
    }

    public void setSources(List<SourceReference> sources) {
        this.sources = sources;
    }

    private String conversationId;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
