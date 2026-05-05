package com.cardamage.api.model.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatRuntimeStatusResponse {
    @JsonProperty("chat_mode")
    private String chatMode;
    @JsonProperty("ollama_url")
    private String ollamaUrl;
    @JsonProperty("ollama_model")
    private String ollamaModel;
    @JsonProperty("ollama_available")
    private boolean ollamaAvailable;

    public ChatRuntimeStatusResponse(String chatMode, String ollamaUrl, String ollamaModel, boolean ollamaAvailable) {
        this.chatMode = chatMode;
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.ollamaAvailable = ollamaAvailable;
    }

    public String getChatMode() {
        return chatMode;
    }

    public String getOllamaUrl() {
        return ollamaUrl;
    }

    public String getOllamaModel() {
        return ollamaModel;
    }

    public boolean isOllamaAvailable() {
        return ollamaAvailable;
    }
}
