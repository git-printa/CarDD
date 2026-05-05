package com.cardamage.api.model.chat;

public class ChatSkillInfo {
    private String id;
    private String description;
    private String requiredRole;

    public ChatSkillInfo(String id, String description, String requiredRole) {
        this.id = id;
        this.description = description;
        this.requiredRole = requiredRole;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getRequiredRole() {
        return requiredRole;
    }
}
