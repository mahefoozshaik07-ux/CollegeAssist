package com.collegeassist.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_conversations", indexes = {
        @Index(name = "idx_conv_student_email", columnList = "studentEmail"),
        @Index(name = "idx_conv_updated_at", columnList = "updatedAt")
})
public class Conversation {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(nullable = false, length = 120)
    private String studentEmail;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Long createdAt;

    @Column(nullable = false)
    private Long updatedAt;

    public Conversation() {
    }

    public Conversation(String id, String studentEmail, String title, Long createdAt, Long updatedAt) {
        this.id = id;
        this.studentEmail = studentEmail;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
}
