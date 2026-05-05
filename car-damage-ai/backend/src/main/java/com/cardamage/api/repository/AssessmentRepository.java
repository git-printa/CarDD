package com.cardamage.api.repository;

import com.cardamage.api.model.AssessmentItem;
import com.cardamage.api.model.DamageCostItem;
import com.cardamage.api.model.UploadBatchResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AssessmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public AssessmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertBatch(UploadBatchResponse response) {
        int updatedBatch = jdbcTemplate.update(
            "UPDATE upload_batches SET vehicle_id = ?, total_files = ?, created_at = ? WHERE batch_id = ?",
            response.getVehicleId(),
            response.getTotalFiles(),
            Timestamp.from(response.getCreatedAt()),
            response.getBatchId()
        );
        if (updatedBatch == 0) {
            jdbcTemplate.update(
                "INSERT INTO upload_batches (batch_id, vehicle_id, total_files, created_at) VALUES (?, ?, ?, ?)",
                response.getBatchId(),
                response.getVehicleId(),
                response.getTotalFiles(),
                Timestamp.from(response.getCreatedAt())
            );
        }
        for (AssessmentItem item : response.getItems()) {
            int updatedImage = jdbcTemplate.update(
                "UPDATE assessment_images SET batch_id = ?, original_filename = ?, stored_filename = ?, content_type = ?, size_bytes = ?, width = ?, height = ?, status = ?, uploaded_at = ? "
                    + "WHERE image_id = ?",
                response.getBatchId(),
                item.getOriginalFilename(),
                item.getStoredFilename(),
                item.getContentType(),
                item.getSizeBytes(),
                item.getWidth(),
                item.getHeight(),
                item.getStatus(),
                Timestamp.from(item.getUploadedAt()),
                item.getImageId()
            );
            if (updatedImage == 0) {
                jdbcTemplate.update(
                    "INSERT INTO assessment_images (image_id, batch_id, original_filename, stored_filename, content_type, size_bytes, width, height, status, uploaded_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    item.getImageId(),
                    response.getBatchId(),
                    item.getOriginalFilename(),
                    item.getStoredFilename(),
                    item.getContentType(),
                    item.getSizeBytes(),
                    item.getWidth(),
                    item.getHeight(),
                    item.getStatus(),
                    Timestamp.from(item.getUploadedAt())
                );
            }
        }
    }

    public List<UploadBatchResponse> listBatches() {
        List<BatchRow> batchRows = jdbcTemplate.query(
            "SELECT batch_id, vehicle_id, total_files, created_at FROM upload_batches ORDER BY created_at DESC",
            this::mapBatchRow
        );
        if (batchRows.isEmpty()) {
            return List.of();
        }
        List<AssessmentItemRow> itemRows = jdbcTemplate.query(
            "SELECT image_id, batch_id, original_filename, stored_filename, content_type, size_bytes, width, height, status, uploaded_at "
                + "FROM assessment_images ORDER BY uploaded_at ASC",
            this::mapAssessmentItemRow
        );
        Map<String, List<AssessmentItem>> byBatch = new LinkedHashMap<>();
        for (AssessmentItemRow row : itemRows) {
            byBatch.computeIfAbsent(row.batchId, k -> new ArrayList<>()).add(row.toAssessmentItem());
        }
        List<UploadBatchResponse> out = new ArrayList<>();
        for (BatchRow row : batchRows) {
            List<AssessmentItem> items = byBatch.getOrDefault(row.batchId, List.of());
            out.add(new UploadBatchResponse(row.batchId, row.vehicleId, row.totalFiles, row.createdAt, items));
        }
        return out;
    }

    public UploadBatchResponse findBatch(String batchId) {
        List<BatchRow> rows = jdbcTemplate.query(
            "SELECT batch_id, vehicle_id, total_files, created_at FROM upload_batches WHERE batch_id = ?",
            this::mapBatchRow,
            batchId
        );
        if (rows.isEmpty()) {
            return null;
        }
        List<AssessmentItem> items = jdbcTemplate.query(
            "SELECT image_id, batch_id, original_filename, stored_filename, content_type, size_bytes, width, height, status, uploaded_at "
                + "FROM assessment_images WHERE batch_id = ? ORDER BY uploaded_at ASC",
            (rs, rowNum) -> mapAssessmentItemRow(rs, rowNum).toAssessmentItem(),
            batchId
        );
        BatchRow row = rows.get(0);
        return new UploadBatchResponse(row.batchId, row.vehicleId, row.totalFiles, row.createdAt, items);
    }

    public AssessmentItemRow findImageById(String imageId) {
        List<AssessmentItemRow> rows = jdbcTemplate.query(
            "SELECT image_id, batch_id, original_filename, stored_filename, content_type, size_bytes, width, height, status, uploaded_at "
                + "FROM assessment_images WHERE image_id = ?",
            this::mapAssessmentItemRow,
            imageId
        );
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    public void replaceDetections(String batchId, List<DamageCostItem> detections, Instant createdAt) {
        jdbcTemplate.update("DELETE FROM assessment_detections WHERE batch_id = ?", batchId);
        int seq = 0;
        for (DamageCostItem item : detections) {
            List<Integer> box = item.getBox() == null ? List.of() : item.getBox();
            int x = box.size() > 0 ? box.get(0) : 0;
            int y = box.size() > 1 ? box.get(1) : 0;
            int w = box.size() > 2 ? box.get(2) : 0;
            int h = box.size() > 3 ? box.get(3) : 0;
            jdbcTemplate.update(
                "INSERT INTO assessment_detections (batch_id, image_id, damage_type, confidence, box_x, box_y, box_w, box_h, part, estimated_cost, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batchId,
                item.getImageId(),
                item.getType(),
                item.getConfidence(),
                x,
                y,
                w,
                h,
                item.getPart(),
                item.getEstimatedCost(),
                Timestamp.from(createdAt.plusMillis(seq))
            );
            seq++;
        }
    }

    public long countBatchesSince(Instant sinceInclusive) {
        return queryLongWithSince("SELECT COUNT(*) FROM upload_batches WHERE created_at >= ?", sinceInclusive);
    }

    public long countImagesSince(Instant sinceInclusive) {
        return queryLongWithSince("SELECT COUNT(*) FROM assessment_images WHERE uploaded_at >= ?", sinceInclusive);
    }

    public long countDetectionsSince(Instant sinceInclusive) {
        return queryLongWithSince("SELECT COUNT(*) FROM assessment_detections WHERE created_at >= ?", sinceInclusive);
    }

    public long sumEstimatedCostSince(Instant sinceInclusive) {
        return queryLongWithSince("SELECT COALESCE(SUM(estimated_cost), 0) FROM assessment_detections WHERE created_at >= ?", sinceInclusive);
    }

    public List<BreakdownRow> topDamageTypesSince(Instant sinceInclusive, int limit) {
        return jdbcTemplate.query(
            "SELECT damage_type AS name, COUNT(*) AS count_value, COALESCE(SUM(estimated_cost), 0) AS cost_value "
                + "FROM assessment_detections WHERE created_at >= ? "
                + "GROUP BY damage_type ORDER BY count_value DESC, cost_value DESC LIMIT ?",
            breakdownRowMapper(),
            Timestamp.from(sinceInclusive),
            limit
        );
    }

    public List<BreakdownRow> costByPartSince(Instant sinceInclusive, int limit) {
        return jdbcTemplate.query(
            "SELECT COALESCE(part, 'unknown') AS name, COUNT(*) AS count_value, COALESCE(SUM(estimated_cost), 0) AS cost_value "
                + "FROM assessment_detections WHERE created_at >= ? "
                + "GROUP BY COALESCE(part, 'unknown') ORDER BY cost_value DESC, count_value DESC LIMIT ?",
            breakdownRowMapper(),
            Timestamp.from(sinceInclusive),
            limit
        );
    }

    public List<TrendRow> dailyTrendSince(Instant sinceInclusive) {
        return jdbcTemplate.query(
            "SELECT CAST(created_at AS DATE) AS day_value, COUNT(*) AS count_value, COALESCE(SUM(estimated_cost), 0) AS cost_value "
                + "FROM assessment_detections WHERE created_at >= ? "
                + "GROUP BY CAST(created_at AS DATE) ORDER BY day_value ASC",
            (rs, rowNum) -> new TrendRow(rs.getDate("day_value").toString(), rs.getLong("count_value"), rs.getLong("cost_value")),
            Timestamp.from(sinceInclusive)
        );
    }

    private long queryLongWithSince(String sql, Instant sinceInclusive) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(sinceInclusive));
        return value == null ? 0L : value;
    }

    private RowMapper<BreakdownRow> breakdownRowMapper() {
        return (rs, rowNum) -> new BreakdownRow(rs.getString("name"), rs.getLong("count_value"), rs.getLong("cost_value"));
    }

    private BatchRow mapBatchRow(ResultSet rs, int rowNum) throws SQLException {
        return new BatchRow(
            rs.getString("batch_id"),
            rs.getString("vehicle_id"),
            rs.getInt("total_files"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private AssessmentItemRow mapAssessmentItemRow(ResultSet rs, int rowNum) throws SQLException {
        return new AssessmentItemRow(
            rs.getString("image_id"),
            rs.getString("batch_id"),
            rs.getString("original_filename"),
            rs.getString("stored_filename"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            rs.getInt("width"),
            rs.getInt("height"),
            rs.getString("status"),
            rs.getTimestamp("uploaded_at").toInstant()
        );
    }

    private static class BatchRow {
        private final String batchId;
        private final String vehicleId;
        private final int totalFiles;
        private final Instant createdAt;

        private BatchRow(String batchId, String vehicleId, int totalFiles, Instant createdAt) {
            this.batchId = batchId;
            this.vehicleId = vehicleId;
            this.totalFiles = totalFiles;
            this.createdAt = createdAt;
        }
    }

    public static class AssessmentItemRow {
        private final String imageId;
        private final String batchId;
        private final String originalFilename;
        private final String storedFilename;
        private final String contentType;
        private final long sizeBytes;
        private final int width;
        private final int height;
        private final String status;
        private final Instant uploadedAt;

        private AssessmentItemRow(
            String imageId,
            String batchId,
            String originalFilename,
            String storedFilename,
            String contentType,
            long sizeBytes,
            int width,
            int height,
            String status,
            Instant uploadedAt
        ) {
            this.imageId = imageId;
            this.batchId = batchId;
            this.originalFilename = originalFilename;
            this.storedFilename = storedFilename;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.width = width;
            this.height = height;
            this.status = status;
            this.uploadedAt = uploadedAt;
        }

        public String getBatchId() {
            return batchId;
        }

        public AssessmentItem toAssessmentItem() {
            return new AssessmentItem(imageId, originalFilename, storedFilename, contentType, sizeBytes, width, height, status, uploadedAt);
        }
    }

    public static class BreakdownRow {
        private final String name;
        private final long count;
        private final long totalCost;

        public BreakdownRow(String name, long count, long totalCost) {
            this.name = name;
            this.count = count;
            this.totalCost = totalCost;
        }

        public String getName() {
            return name;
        }

        public long getCount() {
            return count;
        }

        public long getTotalCost() {
            return totalCost;
        }
    }

    public static class TrendRow {
        private final String day;
        private final long detections;
        private final long totalCost;

        public TrendRow(String day, long detections, long totalCost) {
            this.day = day;
            this.detections = detections;
            this.totalCost = totalCost;
        }

        public String getDay() {
            return day;
        }

        public long getDetections() {
            return detections;
        }

        public long getTotalCost() {
            return totalCost;
        }
    }
}
