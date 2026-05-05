package com.cardamage.api.service;

import com.cardamage.api.config.AppProperties;
import com.cardamage.api.exception.NotFoundException;
import com.cardamage.api.model.AssessmentItem;
import com.cardamage.api.model.UploadBatchResponse;
import com.cardamage.api.repository.AssessmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class AssessmentStoreService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentStoreService.class);

    static final String BATCH_METADATA_FILE = "batch-metadata.json";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final AssessmentRepository assessmentRepository;
    private final Map<String, UploadBatchResponse> batches = new ConcurrentHashMap<>();

    public AssessmentStoreService(AppProperties appProperties, ObjectMapper objectMapper, AssessmentRepository assessmentRepository) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.assessmentRepository = assessmentRepository;
    }

    @PostConstruct
    public void loadPersistedBatches() {
        batches.clear();
        for (UploadBatchResponse batch : assessmentRepository.listBatches()) {
            if (batch.getBatchId() != null) {
                batches.put(batch.getBatchId(), batch);
            }
        }
        if (!batches.isEmpty()) {
            log.info("Loaded {} persisted batches from database", batches.size());
            return;
        }
        // compatibility fallback for old file-only state
        Path root = Path.of(appProperties.getUploadDir());
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(this::tryLoadBatchDir);
        } catch (IOException e) {
            log.warn("Could not scan upload dir {}: {}", root, e.getMessage());
        }
    }

    private void tryLoadBatchDir(Path batchDir) {
        Path meta = batchDir.resolve(BATCH_METADATA_FILE);
        if (!Files.isRegularFile(meta)) {
            return;
        }
        try {
            UploadBatchResponse batch = objectMapper.readValue(meta.toFile(), UploadBatchResponse.class);
            if (batch.getBatchId() != null) {
                batches.put(batch.getBatchId(), batch);
                assessmentRepository.upsertBatch(batch);
                log.info("Loaded persisted batch {} from {}", batch.getBatchId(), batchDir);
            }
        } catch (IOException e) {
            log.warn("Could not load batch metadata {}: {}", meta, e.getMessage());
        }
    }

    public UploadBatchResponse createBatch(String vehicleId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("At least one image file is required");
        }

        String batchId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Path root = Path.of(appProperties.getUploadDir(), batchId);

        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create upload directory", e);
        }

        List<AssessmentItem> items = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Only image uploads are supported");
            }

            String imageId = UUID.randomUUID().toString();
            String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
            String extension = extensionOf(originalName);
            String storedFilename = imageId + extension;
            Path destination = root.resolve(storedFilename);

            int width = 0;
            int height = 0;

            try (InputStream in = file.getInputStream()) {
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            } catch (IOException ignored) {
                // Keep dimensions as 0 if unreadable, but still save for later processing.
            }

            try (InputStream uploadStream = file.getInputStream()) {
                Files.copy(uploadStream, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to store image " + originalName, e);
            }

            items.add(new AssessmentItem(
                imageId,
                originalName,
                storedFilename,
                contentType,
                file.getSize(),
                width,
                height,
                "UPLOADED",
                now
            ));
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("No valid image files were uploaded");
        }

        UploadBatchResponse response = new UploadBatchResponse(
            batchId,
            vehicleId == null || vehicleId.isBlank() ? "N/A" : vehicleId,
            items.size(),
            now,
            items
        );

        batches.put(batchId, response);
        persistBatchMetadata(root, response);
        assessmentRepository.upsertBatch(response);
        log.info("Stored batch {} with {} images at {}", batchId, items.size(), root);
        return response;
    }

    private void persistBatchMetadata(Path batchDir, UploadBatchResponse response) {
        Path meta = batchDir.resolve(BATCH_METADATA_FILE);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(meta.toFile(), response);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to persist batch metadata", e);
        }
    }

    public UploadBatchResponse getBatch(String batchId) {
        UploadBatchResponse batch = batches.get(batchId);
        if (batch == null) {
            batch = assessmentRepository.findBatch(batchId);
            if (batch != null) {
                batches.put(batchId, batch);
            }
        }
        if (batch == null) {
            throw new NotFoundException("Batch not found: " + batchId);
        }
        return batch;
    }

    public List<UploadBatchResponse> listBatches() {
        List<UploadBatchResponse> out = new ArrayList<>(batches.values());
        out.sort(Comparator.comparing(UploadBatchResponse::getCreatedAt).reversed());
        return out;
    }

    public ResolvedImage resolveImageById(String imageId) {
        for (UploadBatchResponse batch : batches.values()) {
            for (AssessmentItem item : batch.getItems()) {
                if (item.getImageId() != null && item.getImageId().equals(imageId)) {
                    Path path = Path.of(appProperties.getUploadDir(), batch.getBatchId(), item.getStoredFilename());
                    return new ResolvedImage(batch.getBatchId(), item, path);
                }
            }
        }
        throw new NotFoundException("Image not found: " + imageId);
    }

    private String extensionOf(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx == -1 || idx == filename.length() - 1) {
            return ".jpg";
        }
        return filename.substring(idx);
    }

    public static class ResolvedImage {
        private final String batchId;
        private final AssessmentItem item;
        private final Path path;

        public ResolvedImage(String batchId, AssessmentItem item, Path path) {
            this.batchId = batchId;
            this.item = item;
            this.path = path;
        }

        public String getBatchId() {
            return batchId;
        }

        public AssessmentItem getItem() {
            return item;
        }

        public Path getPath() {
            return path;
        }
    }
}
