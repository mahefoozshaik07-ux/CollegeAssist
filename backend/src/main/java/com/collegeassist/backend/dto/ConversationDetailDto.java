package com.collegeassist.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class ConversationDetailDto {

    private String id;
    private String title;
    private String studentEmail;
    private Long createdAt;
    private Long updatedAt;
    private List<ChatMessageDto> messages = new ArrayList<>();

    public ConversationDetailDto() {
    }

    public ConversationDetailDto(String id, String title, String studentEmail, Long createdAt, Long updatedAt, List<ChatMessageDto> messages) {
        this.id = id;
        this.title = title;
        this.studentEmail = studentEmail;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messages = (messages != null) ? messages : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessageDto> messages) {
        this.messages = (messages != null) ? messages : new ArrayList<>();
    }
}
