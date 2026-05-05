CREATE TABLE IF NOT EXISTS upload_batches (
    batch_id VARCHAR(64) PRIMARY KEY,
    vehicle_id VARCHAR(128) NOT NULL,
    total_files INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS assessment_images (
    image_id VARCHAR(64) PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    stored_filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    status VARCHAR(64) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_assessment_images_batch FOREIGN KEY (batch_id) REFERENCES upload_batches(batch_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assessment_images_batch_id ON assessment_images(batch_id);
CREATE INDEX IF NOT EXISTS idx_assessment_images_uploaded_at ON assessment_images(uploaded_at);

CREATE TABLE IF NOT EXISTS assessment_detections (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    image_id VARCHAR(64) NOT NULL,
    damage_type VARCHAR(64) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    box_x INTEGER NOT NULL,
    box_y INTEGER NOT NULL,
    box_w INTEGER NOT NULL,
    box_h INTEGER NOT NULL,
    part VARCHAR(64),
    estimated_cost INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assessment_detections_batch FOREIGN KEY (batch_id) REFERENCES upload_batches(batch_id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_detections_image FOREIGN KEY (image_id) REFERENCES assessment_images(image_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assessment_detections_batch_id ON assessment_detections(batch_id);
CREATE INDEX IF NOT EXISTS idx_assessment_detections_created_at ON assessment_detections(created_at);
CREATE INDEX IF NOT EXISTS idx_assessment_detections_damage_type ON assessment_detections(damage_type);
CREATE INDEX IF NOT EXISTS idx_assessment_detections_part ON assessment_detections(part);

CREATE TABLE IF NOT EXISTS training_labels (
    id BIGSERIAL PRIMARY KEY,
    image_id VARCHAR(64) NOT NULL,
    type VARCHAR(64) NOT NULL,
    damage_type VARCHAR(64) NOT NULL,
    part VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    note TEXT,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    w INTEGER NOT NULL,
    h INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_training_labels_image FOREIGN KEY (image_id) REFERENCES assessment_images(image_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_training_labels_image_id ON training_labels(image_id);

CREATE TABLE IF NOT EXISTS training_runs (
    run_id VARCHAR(64) PRIMARY KEY,
    data_yaml TEXT NOT NULL,
    command TEXT NOT NULL,
    workdir TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    exit_code INTEGER,
    best_pt TEXT,
    last_error TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_training_runs_started_at ON training_runs(started_at);

CREATE TABLE IF NOT EXISTS training_run_logs (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    sequence_no INTEGER NOT NULL,
    line TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_training_run_logs_run FOREIGN KEY (run_id) REFERENCES training_runs(run_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_training_run_logs_sequence ON training_run_logs(run_id, sequence_no);

CREATE TABLE IF NOT EXISTS datasets (
    dataset_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    source VARCHAR(128) NOT NULL,
    license VARCHAR(128),
    version VARCHAR(64),
    class_mapping TEXT,
    sample_count INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_registry (
    model_id VARCHAR(64) PRIMARY KEY,
    source_run_id VARCHAR(64),
    model_path TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_role VARCHAR(64) NOT NULL,
    prompt TEXT NOT NULL,
    chat_mode VARCHAR(64) NOT NULL,
    selected_skill VARCHAR(128) NOT NULL,
    parameters_json TEXT,
    response_summary TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_audit_logs_created_at ON chat_audit_logs(created_at);
