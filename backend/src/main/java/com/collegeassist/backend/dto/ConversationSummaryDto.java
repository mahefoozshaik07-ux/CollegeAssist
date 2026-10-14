package com.collegeassist.backend.dto;

public class ConversationSummaryDto {

    private String id;
    private String title;
    private String studentEmail;
    private Long createdAt;
    private Long updatedAt;
    private long messageCount;

    public ConversationSummaryDto() {
    }

    public ConversationSummaryDto(String id, String title, String studentEmail, Long createdAt, Long updatedAt, long messageCount) {
        this.id = id;
        this.title = title;
        this.studentEmail = studentEmail;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
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

    public long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(long messageCount) {
        this.messageCount = messageCount;
    }
}
