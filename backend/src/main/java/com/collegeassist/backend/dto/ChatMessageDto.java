package com.collegeassist.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageDto {

    private String id;
    private String sender;
    private String text;
    private String timestamp;
    private String status;
    private List<SourceReference> sources = new ArrayList<>();

    public ChatMessageDto() {
    }

    public ChatMessageDto(String id, String sender, String text, String timestamp, String status, List<SourceReference> sources) {
        this.id = id;
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.status = status;
        this.sources = (sources != null) ? sources : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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

    public List<SourceReference> getSources() {
        return sources;
    }

    public void setSources(List<SourceReference> sources) {
        this.sources = (sources != null) ? sources : new ArrayList<>();
    }
}
