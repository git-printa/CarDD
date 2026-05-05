package com.cardamage.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String uploadDir;
    private String aiServiceUrl;
    private String chatMode;
    private String ollamaUrl;
    private String ollamaModel;

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getAiServiceUrl() {
        return aiServiceUrl;
    }

    public void setAiServiceUrl(String aiServiceUrl) {
        this.aiServiceUrl = aiServiceUrl;
    }

    public String getChatMode() {
        return chatMode;
    }

    public void setChatMode(String chatMode) {
        this.chatMode = chatMode;
    }

    public String getOllamaUrl() {
        return ollamaUrl;
    }

    public void setOllamaUrl(String ollamaUrl) {
        this.ollamaUrl = ollamaUrl;
    }

    public String getOllamaModel() {
        return ollamaModel;
    }

    public void setOllamaModel(String ollamaModel) {
        this.ollamaModel = ollamaModel;
    }
}
