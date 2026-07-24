CREATE TABLE chat_message_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_id BIGINT NULL,
    rank_no INT NOT NULL,
    retrieval_score DECIMAL(8,4) NOT NULL,
    snippet_snapshot TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_chat_message_source_message_rank (message_id, rank_no),
    KEY idx_chat_message_source_document_id (document_id),
    CONSTRAINT fk_chat_message_source_message FOREIGN KEY (message_id) REFERENCES chat_message(id),
    CONSTRAINT fk_chat_message_source_document FOREIGN KEY (document_id) REFERENCES kb_document(id),
    CONSTRAINT fk_chat_message_source_chunk FOREIGN KEY (chunk_id) REFERENCES kb_chunk(id)
);

CREATE TABLE ticket_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    previous_status VARCHAR(32),
    next_status VARCHAR(32) NOT NULL,
    operation_note TEXT,
    created_at DATETIME NOT NULL,
    KEY idx_ticket_operation_log_ticket_created (ticket_id, created_at),
    CONSTRAINT fk_ticket_operation_log_ticket FOREIGN KEY (ticket_id) REFERENCES support_ticket(id),
    CONSTRAINT fk_ticket_operation_log_operator FOREIGN KEY (operator_id) REFERENCES user_account(id)
);
