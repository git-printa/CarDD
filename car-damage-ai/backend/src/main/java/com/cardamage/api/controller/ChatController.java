package com.cardamage.api.controller;

import com.cardamage.api.model.chat.ChatQueryRequest;
import com.cardamage.api.model.chat.ChatQueryResponse;
import com.cardamage.api.model.chat.ChatRuntimeStatusResponse;
import com.cardamage.api.model.chat.ChatSkillInfo;
import com.cardamage.api.config.AppProperties;
import com.cardamage.api.service.ChatAnalyticsService;
import com.cardamage.api.service.OllamaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatAnalyticsService chatAnalyticsService;
    private final AppProperties appProperties;
    private final OllamaService ollamaService;

    public ChatController(ChatAnalyticsService chatAnalyticsService, AppProperties appProperties, OllamaService ollamaService) {
        this.chatAnalyticsService = chatAnalyticsService;
        this.appProperties = appProperties;
        this.ollamaService = ollamaService;
    }

    @GetMapping("/skills")
    public ResponseEntity<List<ChatSkillInfo>> skills() {
        return ResponseEntity.ok(chatAnalyticsService.availableSkills());
    }

    @GetMapping("/runtime")
    public ResponseEntity<ChatRuntimeStatusResponse> runtime() {
        return ResponseEntity.ok(
            new ChatRuntimeStatusResponse(
                appProperties.getChatMode(),
                appProperties.getOllamaUrl(),
                appProperties.getOllamaModel(),
                ollamaService.isAvailable()
            )
        );
    }

    @PostMapping("/query")
    public ResponseEntity<ChatQueryResponse> query(
        @RequestHeader(name = "X-Role", required = false) String role,
        @RequestBody ChatQueryRequest request
    ) {
        return ResponseEntity.ok(chatAnalyticsService.query(role, request.getPrompt(), request.getTimeRangeDays()));
    }
}
