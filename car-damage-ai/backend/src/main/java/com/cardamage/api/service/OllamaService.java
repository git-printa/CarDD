package com.cardamage.api.service;

import com.cardamage.api.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class OllamaService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OllamaService(RestTemplate restTemplate, ObjectMapper objectMapper, AppProperties appProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public String askForSkillJson(String prompt) {
        return ask(prompt);
    }

    public String ask(String prompt) {
        String endpoint = appProperties.getOllamaUrl() + "/api/generate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = Map.of(
            "model", appProperties.getOllamaModel(),
            "stream", false,
            "prompt", prompt
        );
        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(payload, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Ollama request failed");
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode text = root.get("response");
            if (text == null || text.asText().isBlank()) {
                throw new IllegalStateException("Ollama returned empty response");
            }
            return text.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Ollama response", e);
        }
    }

    public boolean isAvailable() {
        String endpoint = appProperties.getOllamaUrl() + "/api/tags";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(endpoint, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
