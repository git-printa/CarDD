package com.cardamage.api.repository;

import com.cardamage.api.model.training.TrainingLabel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class TrainingRepository {

    private final JdbcTemplate jdbcTemplate;

    public TrainingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TrainingLabel> loadLabels(String imageId) {
        return jdbcTemplate.query(
            "SELECT type, damage_type, part, severity, COALESCE(note, '') AS note, x, y, w, h "
                + "FROM training_labels WHERE image_id = ? ORDER BY id ASC",
            (rs, rowNum) -> new TrainingLabel(
                rs.getString("type"),
                rs.getString("damage_type"),
                rs.getString("part"),
                rs.getString("severity"),
                rs.getString("note"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("w"),
                rs.getInt("h")
            ),
            imageId
        );
    }

    public void replaceLabels(String imageId, List<TrainingLabel> labels) {
        jdbcTemplate.update("DELETE FROM training_labels WHERE image_id = ?", imageId);
        for (TrainingLabel label : labels) {
            jdbcTemplate.update(
                "INSERT INTO training_labels (image_id, type, damage_type, part, severity, note, x, y, w, h, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                imageId,
                label.getType(),
                label.getDamageType(),
                label.getPart(),
                label.getSeverity(),
                label.getNote(),
                label.getX(),
                label.getY(),
                label.getW(),
                label.getH(),
                Timestamp.from(Instant.now())
            );
        }
    }

    public void createTrainingRun(String runId, String dataYaml, String command, String workdir, Instant startedAt) {
        jdbcTemplate.update(
            "INSERT INTO training_runs (run_id, data_yaml, command, workdir, status, started_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            runId,
            dataYaml,
            command,
            workdir,
            "running",
            Timestamp.from(startedAt),
            Timestamp.from(startedAt)
        );
    }

    public void updateTrainingRun(
        String runId,
        String status,
        Integer exitCode,
        String bestPt,
        String lastError,
        Instant finishedAt
    ) {
        jdbcTemplate.update(
            "UPDATE training_runs SET status = ?, exit_code = ?, best_pt = ?, last_error = ?, finished_at = ?, updated_at = ? WHERE run_id = ?",
            status,
            exitCode,
            bestPt,
            lastError,
            finishedAt == null ? null : Timestamp.from(finishedAt),
            Timestamp.from(Instant.now()),
            runId
        );
    }

    public void appendTrainingRunLog(String runId, int sequenceNo, String line) {
        jdbcTemplate.update(
            "INSERT INTO training_run_logs (run_id, sequence_no, line, created_at) VALUES (?, ?, ?, ?)",
            runId,
            sequenceNo,
            line,
            Timestamp.from(Instant.now())
        );
    }
}
