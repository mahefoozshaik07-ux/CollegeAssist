package com.collegeassist.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatRequest {

    @NotBlank(message = "Question or query message must not be blank")
    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    private String message;

    private String conversationId;

    private String studentEmail;

    public ChatRequest() {
    }

    public ChatRequest(String message, String conversationId) {
        this.message = message;
        this.conversationId = conversationId;
    }

    public ChatRequest(String message, String conversationId, String studentEmail) {
        this.message = message;
        this.conversationId = conversationId;
        this.studentEmail = studentEmail;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    // Support alias 'email' from frontend payload
    public String getEmail() {
        return studentEmail;
    }

    public void setEmail(String email) {
        if (this.studentEmail == null || this.studentEmail.isBlank()) {
            this.studentEmail = email;
        }
    }
}
