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
