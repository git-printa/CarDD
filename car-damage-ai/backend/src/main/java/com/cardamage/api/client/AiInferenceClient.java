package com.cardamage.api.client;

import com.cardamage.api.model.ai.AiPredictResponse;

import java.nio.file.Path;

public interface AiInferenceClient {

    AiPredictResponse predict(Path imagePath, String originalFilename, String contentType);
}
