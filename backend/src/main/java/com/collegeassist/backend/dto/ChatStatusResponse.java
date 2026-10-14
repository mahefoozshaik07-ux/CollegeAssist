package com.collegeassist.backend.dto;

import java.util.List;

public class ChatStatusResponse {

    private String aiProvider;
    private boolean configured;
    private String model;
    private String knowledgeBaseDirectory;
    private List<String> categories;
    private String message;

    public ChatStatusResponse() {
    }

    public ChatStatusResponse(String aiProvider, boolean configured, String model, String knowledgeBaseDirectory, List<String> categories, String message) {
        this.aiProvider = aiProvider;
        this.configured = configured;
        this.model = model;
        this.knowledgeBaseDirectory = knowledgeBaseDirectory;
        this.categories = categories;
        this.message = message;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getKnowledgeBaseDirectory() {
        return knowledgeBaseDirectory;
    }

    public void setKnowledgeBaseDirectory(String knowledgeBaseDirectory) {
        this.knowledgeBaseDirectory = knowledgeBaseDirectory;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
