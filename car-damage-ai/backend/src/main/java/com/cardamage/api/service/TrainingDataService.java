package com.cardamage.api.service;

import com.cardamage.api.model.AssessmentItem;
import com.cardamage.api.model.UploadBatchResponse;
import com.cardamage.api.model.training.TrainingImageSummary;
import com.cardamage.api.model.training.TrainingLabel;
import com.cardamage.api.repository.TrainingRepository;
import com.cardamage.api.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class TrainingDataService {

    private static final List<String> CLASSES = List.of("scratch", "dent", "crack");
    private static final List<String> DAMAGE_TYPES = List.of(
        "surface_scratch",
        "paint_scuff",
        "clearcoat_scratch",
        "dent",
        "crease",
        "panel_deformation",
        "bumper_deformation",
        "misalignment",
        "crack",
        "broken_light",
        "shattered_glass",
        "hole_puncture"
    );
    private static final List<String> PARTS = List.of(
        "front_bumper",
        "rear_bumper",
        "hood",
        "trunk",
        "roof",
        "front_left_door",
        "front_right_door",
        "rear_left_door",
        "rear_right_door",
        "front_left_fender",
        "front_right_fender",
        "rear_left_quarter",
        "rear_right_quarter",
        "left_headlight",
        "right_headlight",
        "left_taillight",
        "right_taillight",
        "grille",
        "windshield",
        "rear_glass",
        "side_mirror_left",
        "side_mirror_right",
        "wheel_arch"
    );
    private static final List<String> SEVERITIES = List.of("minor", "moderate", "severe");

    private final AssessmentStoreService assessmentStoreService;
    private final TrainingRepository trainingRepository;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public TrainingDataService(
        AssessmentStoreService assessmentStoreService,
        TrainingRepository trainingRepository,
        ObjectMapper objectMapper,
        AppProperties appProperties
    ) {
        this.assessmentStoreService = assessmentStoreService;
        this.trainingRepository = trainingRepository;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public List<TrainingImageSummary> listImages() {
        List<TrainingImageSummary> out = new ArrayList<>();
        List<UploadBatchResponse> batches = assessmentStoreService.listBatches();
        for (UploadBatchResponse batch : batches) {
            for (AssessmentItem it : batch.getItems()) {
                out.add(new TrainingImageSummary(
                    it.getImageId(),
                    batch.getBatchId(),
                    it.getOriginalFilename(),
                    it.getWidth(),
                    it.getHeight()
                ));
            }
        }
        out.sort(Comparator.comparing(TrainingImageSummary::getImageId).reversed());
        return out;
    }

    public AssessmentStoreService.ResolvedImage resolveImage(String imageId) {
        return assessmentStoreService.resolveImageById(imageId);
    }

    public byte[] readImageBytes(String imageId) throws IOException {
        AssessmentStoreService.ResolvedImage resolved = resolveImage(imageId);
        return Files.readAllBytes(resolved.getPath());
    }

    public String imageMediaType(String imageId) {
        AssessmentStoreService.ResolvedImage resolved = resolveImage(imageId);
        String ct = resolved.getItem().getContentType();
        if (ct == null || ct.isBlank()) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        return ct;
    }

    public List<TrainingLabel> loadLabels(String imageId) {
        List<TrainingLabel> persisted = trainingRepository.loadLabels(imageId);
        if (!persisted.isEmpty()) {
            return persisted;
        }
        Path jsonPath = labelJsonPath(imageId);
        if (!Files.isRegularFile(jsonPath)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(jsonPath.toFile(), new TypeReference<List<TrainingLabel>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read labels for image " + imageId, e);
        }
    }

    public List<TrainingLabel> saveLabels(String imageId, List<TrainingLabel> labels) {
        AssessmentStoreService.ResolvedImage resolved = resolveImage(imageId);
        List<TrainingLabel> sanitized = sanitize(labels, resolved.getItem().getWidth(), resolved.getItem().getHeight());
        trainingRepository.replaceLabels(imageId, sanitized);
        Path jsonPath = labelJsonPath(imageId);
        Path txtPath = labelTxtPath(imageId);
        try {
            Files.createDirectories(jsonPath.getParent());
            Files.createDirectories(txtPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), sanitized);
            writeYoloTxt(txtPath, sanitized, resolved.getItem().getWidth(), resolved.getItem().getHeight());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to save labels", e);
        }
        return sanitized;
    }

    public ExportResult exportYoloDataset() {
        Path uploadRoot = Path.of(appProperties.getUploadDir());
        Path exportRoot = uploadRoot.resolve("training-export");
        Path imagesTrain = exportRoot.resolve("images/train");
        Path imagesVal = exportRoot.resolve("images/val");
        Path labelsTrain = exportRoot.resolve("labels/train");
        Path labelsVal = exportRoot.resolve("labels/val");
        try {
            Files.createDirectories(imagesTrain);
            Files.createDirectories(imagesVal);
            Files.createDirectories(labelsTrain);
            Files.createDirectories(labelsVal);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create export directories", e);
        }

        List<TrainingImageSummary> images = listImages();
        int count = 0;
        for (TrainingImageSummary img : images) {
            AssessmentStoreService.ResolvedImage resolved;
            try {
                resolved = resolveImage(img.getImageId());
            } catch (Exception ignored) {
                continue;
            }
            Path txt = labelTxtPath(img.getImageId());
            if (!Files.isRegularFile(txt)) {
                continue;
            }
            String stem = img.getImageId();
            String ext = extensionOf(resolved.getItem().getStoredFilename());
            boolean isVal = Math.floorMod(stem.hashCode(), 5) == 0;
            Path dstImg = (isVal ? imagesVal : imagesTrain).resolve(stem + ext);
            Path dstLbl = (isVal ? labelsVal : labelsTrain).resolve(stem + ".txt");
            try {
                Files.copy(resolved.getPath(), dstImg, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(txt, dstLbl, StandardCopyOption.REPLACE_EXISTING);
                count++;
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to export image " + img.getImageId(), e);
            }
        }

        Path dataYaml = exportRoot.resolve("data.yaml");
        String yaml = "path: " + exportRoot.toAbsolutePath() + "\n"
            + "train: images/train\n"
            + "val: images/val\n"
            + "nc: 3\n"
            + "names: [\"scratch\", \"dent\", \"crack\"]\n";
        try {
            Files.writeString(dataYaml, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to write data.yaml", e);
        }
        return new ExportResult(exportRoot.toString(), dataYaml.toString(), count, CLASSES);
    }

    private List<TrainingLabel> sanitize(List<TrainingLabel> labels, int imageW, int imageH) {
        List<TrainingLabel> out = new ArrayList<>();
        if (labels == null) {
            return out;
        }
        for (TrainingLabel l : labels) {
            if (l == null) {
                continue;
            }
            String damageType = normalizeValue(l.getDamageType());
            if (!DAMAGE_TYPES.contains(damageType)) {
                damageType = mapLegacyDamageType(normalizeValue(l.getType()));
            }
            if (!DAMAGE_TYPES.contains(damageType)) {
                continue;
            }
            String modelClass = normalizeValue(l.getType());
            if (!CLASSES.contains(modelClass)) {
                modelClass = classFromDamageType(damageType);
            }
            if (!CLASSES.contains(modelClass)) {
                continue;
            }
            String part = normalizeValue(l.getPart());
            if (!PARTS.contains(part)) {
                part = "front_bumper";
            }
            String severity = normalizeValue(l.getSeverity());
            if (!SEVERITIES.contains(severity)) {
                severity = "moderate";
            }
            int x = Math.max(0, l.getX());
            int y = Math.max(0, l.getY());
            int w = Math.max(1, l.getW());
            int h = Math.max(1, l.getH());
            if (imageW > 0 && imageH > 0) {
                if (x >= imageW || y >= imageH) {
                    continue;
                }
                if (x + w > imageW) {
                    w = imageW - x;
                }
                if (y + h > imageH) {
                    h = imageH - y;
                }
            }
            if (w <= 0 || h <= 0) {
                continue;
            }
            String note = l.getNote() == null ? "" : l.getNote().trim();
            out.add(new TrainingLabel(modelClass, damageType, part, severity, note, x, y, w, h));
        }
        return out;
    }

    private void writeYoloTxt(Path path, List<TrainingLabel> labels, int imageW, int imageH) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (TrainingLabel l : labels) {
            int cls = CLASSES.indexOf(l.getType());
            if (cls < 0 || imageW <= 0 || imageH <= 0) {
                continue;
            }
            double xc = (l.getX() + l.getW() / 2.0) / imageW;
            double yc = (l.getY() + l.getH() / 2.0) / imageH;
            double wn = l.getW() / (double) imageW;
            double hn = l.getH() / (double) imageH;
            sb.append(cls).append(' ')
                .append(String.format(Locale.ROOT, "%.6f %.6f %.6f %.6f", xc, yc, wn, hn))
                .append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private Path labelJsonPath(String imageId) {
        Path uploadRoot = Path.of(appProperties.getUploadDir());
        return uploadRoot.resolve("labels-json").resolve(imageId + ".json");
    }

    private Path labelTxtPath(String imageId) {
        Path uploadRoot = Path.of(appProperties.getUploadDir());
        return uploadRoot.resolve("labels-yolo").resolve(imageId + ".txt");
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return ".jpg";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0) {
            return ".jpg";
        }
        return filename.substring(idx);
    }

    public static class ExportResult {
        private final String datasetPath;
        private final String dataYaml;
        private final int labeledImages;
        private final List<String> classNames;

        public ExportResult(String datasetPath, String dataYaml, int labeledImages, List<String> classNames) {
            this.datasetPath = datasetPath;
            this.dataYaml = dataYaml;
            this.labeledImages = labeledImages;
            this.classNames = classNames;
        }

        public String getDatasetPath() {
            return datasetPath;
        }

        public String getDataYaml() {
            return dataYaml;
        }

        public int getLabeledImages() {
            return labeledImages;
        }

        public List<String> getClassNames() {
            return classNames;
        }
    }

    private static String normalizeValue(String v) {
        if (v == null) {
            return "";
        }
        return v.trim().toLowerCase(Locale.ROOT);
    }

    private static String classFromDamageType(String damageType) {
        if (damageType.contains("scratch") || damageType.contains("scuff")) {
            return "scratch";
        }
        if (
            damageType.equals("dent")
                || damageType.equals("crease")
                || damageType.equals("panel_deformation")
                || damageType.equals("bumper_deformation")
                || damageType.equals("misalignment")
        ) {
            return "dent";
        }
        return "crack";
    }

    private static String mapLegacyDamageType(String legacy) {
        if (legacy.equals("scratch")) {
            return "surface_scratch";
        }
        if (legacy.equals("dent")) {
            return "dent";
        }
        if (legacy.equals("crack")) {
            return "crack";
        }
        return legacy;
    }
}
