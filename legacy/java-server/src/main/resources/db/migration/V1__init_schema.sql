CREATE TABLE kb_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    original_name VARCHAR(512) NOT NULL,
    storage_name VARCHAR(128) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(512),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_kb_document_original_name (original_name),
    KEY idx_kb_document_status (status),
    KEY idx_kb_document_created_at (created_at)
);

CREATE TABLE chat_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_no VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_chat_conversation_no (conversation_no),
    KEY idx_chat_conversation_created_at (created_at)
);

CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    sources_json TEXT,
    retrieval_score DECIMAL(8,4),
    confidence_level VARCHAR(32),
    need_human BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id),
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    KEY idx_chat_message_conversation_id (conversation_id),
    KEY idx_chat_message_created_at (created_at)
);

CREATE TABLE support_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no VARCHAR(64) NOT NULL,
    conversation_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    contact VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    handling_note TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_support_ticket_no (ticket_no),
    CONSTRAINT fk_support_ticket_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id),
    KEY idx_support_ticket_conversation_id (conversation_id),
    KEY idx_support_ticket_status (status),
    KEY idx_support_ticket_created_at (created_at)
);
