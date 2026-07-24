ALTER TABLE kb_document
    DROP INDEX uk_kb_document_original_name,
    ADD COLUMN file_sha256 VARCHAR(64) NULL AFTER file_size,
    ADD COLUMN uploaded_by BIGINT NULL AFTER file_sha256,
    ADD COLUMN lock_version INT NOT NULL DEFAULT 0 AFTER uploaded_by,
    ADD UNIQUE KEY uk_kb_document_sha256 (file_sha256),
    ADD KEY idx_kb_document_status_created (status, created_at),
    ADD KEY idx_kb_document_uploaded_by (uploaded_by),
    ADD CONSTRAINT fk_kb_document_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES user_account(id);

CREATE TABLE document_processing_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    next_retry_at DATETIME NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    error_message VARCHAR(1000),
    lock_version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_document_processing_task_document (document_id),
    KEY idx_document_processing_task_status_retry (status, next_retry_at),
    CONSTRAINT fk_document_processing_task_document FOREIGN KEY (document_id) REFERENCES kb_document(id)
);

CREATE TABLE kb_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    char_count INT NOT NULL,
    vector_point_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_kb_chunk_document_index (document_id, chunk_index),
    KEY idx_kb_chunk_document_id (document_id),
    CONSTRAINT fk_kb_chunk_document FOREIGN KEY (document_id) REFERENCES kb_document(id)
);
