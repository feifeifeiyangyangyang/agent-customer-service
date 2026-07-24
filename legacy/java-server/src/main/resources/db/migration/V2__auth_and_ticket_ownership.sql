CREATE TABLE user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_account_username (username),
    KEY idx_user_account_role (role),
    KEY idx_user_account_status (status),
    CONSTRAINT ck_user_account_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_user_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

ALTER TABLE chat_conversation
    ADD COLUMN user_id BIGINT NULL AFTER id,
    ADD KEY idx_chat_conversation_user_updated (user_id, updated_at),
    ADD CONSTRAINT fk_chat_conversation_user FOREIGN KEY (user_id) REFERENCES user_account(id);

ALTER TABLE support_ticket
    ADD COLUMN user_id BIGINT NULL AFTER id,
    ADD COLUMN handler_id BIGINT NULL AFTER status,
    ADD COLUMN priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL' AFTER handler_id,
    ADD COLUMN resolution TEXT NULL AFTER handling_note,
    ADD COLUMN resolved_at DATETIME NULL AFTER resolution,
    ADD COLUMN lock_version INT NOT NULL DEFAULT 0 AFTER resolved_at,
    ADD KEY idx_support_ticket_user_created (user_id, created_at),
    ADD KEY idx_support_ticket_status_updated (status, updated_at),
    ADD KEY idx_support_ticket_handler_id (handler_id),
    ADD CONSTRAINT fk_support_ticket_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    ADD CONSTRAINT fk_support_ticket_handler FOREIGN KEY (handler_id) REFERENCES user_account(id);

UPDATE support_ticket SET status = 'OPEN' WHERE status = 'PENDING';
