package com.cardamage.api.controller;

import com.cardamage.api.client.AiInferenceClient;
import com.cardamage.api.model.ai.AiDetection;
import com.cardamage.api.model.ai.AiPredictResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiInferenceClient aiInferenceClient;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.service").value("backend"));
    }

    @Test
    void uploadShouldStoreMetadata() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
            "files",
            "car.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "fake-image-content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/assessments/upload")
                .file(image)
                .param("vehicleId", "VH-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vehicleId").value("VH-123"))
            .andExpect(jsonPath("$.totalFiles").value(1))
            .andExpect(jsonPath("$.items[0].originalFilename").value("car.jpg"))
            .andExpect(jsonPath("$.items[0].status").value("UPLOADED"));
    }

    @Test
    void analyzeShouldReturnDamagesAndTotalCost() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
            "files",
            "car.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "fake-image-content".getBytes()
        );

        AiDetection detection = new AiDetection();
        detection.setLabel("scratch");
        detection.setConfidence(0.88);
        detection.setBox(Arrays.asList(63, 51, 244, 32));

        AiPredictResponse aiResponse = new AiPredictResponse();
        aiResponse.setDetections(Arrays.asList(detection));
        aiResponse.setImageWidth(700);
        aiResponse.setImageHeight(410);
        aiResponse.setInferenceMode("mock");

        when(aiInferenceClient.predict(any(Path.class), anyString(), anyString())).thenReturn(aiResponse);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/assessments/upload")
                .file(image)
                .param("vehicleId", "VH-123"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String batchId = uploadJson.get("batchId").asText();

        mockMvc.perform(post("/api/v1/assessments/{batchId}/analyze", batchId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.damages[0].type").value("scratch"))
            .andExpect(jsonPath("$.damages[0].confidence").value(0.88))
            .andExpect(jsonPath("$.damages[0].estimated_cost").exists())
            .andExpect(jsonPath("$.damages[0].estimated_cost_nis").exists())
            .andExpect(jsonPath("$.total_cost").isNumber())
            .andExpect(jsonPath("$.total_cost_nis").isNumber())
            .andExpect(jsonPath("$.currency").value("ILS"))
            .andExpect(jsonPath("$.ai_stack.inference_mode").value("mock"));
    }

    @Test
    void uploadShouldRejectMissingFiles() throws Exception {
        mockMvc.perform(multipart("/api/v1/assessments/upload"))
            .andExpect(status().isBadRequest());
    }
}
