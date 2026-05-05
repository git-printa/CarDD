package com.cardamage.api.client;

import com.cardamage.api.config.AppProperties;
import com.cardamage.api.model.ai.AiPredictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FastApiAiInferenceClient implements AiInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(FastApiAiInferenceClient.class);

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;

    public FastApiAiInferenceClient(RestTemplate restTemplate, AppProperties appProperties) {
        this.restTemplate = restTemplate;
        this.appProperties = appProperties;
    }

    @Override
    public AiPredictResponse predict(Path imagePath, String originalFilename, String contentType) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(imagePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image for AI inference: " + imagePath, e);
        }

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType == null ? MediaType.IMAGE_JPEG_VALUE : contentType));

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return originalFilename;
            }
        }, fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        String url = appProperties.getAiServiceUrl() + "/predict";

        try {
            ResponseEntity<AiPredictResponse> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), AiPredictResponse.class);
            AiPredictResponse payload = response.getBody();
            if (payload == null) {
                throw new IllegalArgumentException("AI service returned empty body");
            }
            return payload;
        } catch (RestClientException ex) {
            log.error("AI inference call failed for {}: {}", imagePath, ex.getMessage());
            throw new IllegalArgumentException("AI inference call failed: " + ex.getMessage(), ex);
        }
    }
}
