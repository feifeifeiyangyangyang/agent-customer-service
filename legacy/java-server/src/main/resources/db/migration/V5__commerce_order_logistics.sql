CREATE TABLE product_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    sale_status VARCHAR(32) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock_quantity INT NOT NULL,
    dispatch_rule VARCHAR(255) NOT NULL,
    after_sale_rule VARCHAR(512) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_product_catalog_code (product_code),
    KEY idx_product_catalog_name (product_name),
    KEY idx_product_catalog_status (sale_status)
);

CREATE TABLE customer_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    paid_at DATETIME NULL,
    expected_ship_at DATETIME NULL,
    shipped_at DATETIME NULL,
    signed_at DATETIME NULL,
    receiver_name VARCHAR(64) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    receiver_address VARCHAR(255) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_customer_order_no (order_no),
    KEY idx_customer_order_user_created (user_id, created_at),
    KEY idx_customer_order_status (status),
    CONSTRAINT fk_customer_order_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_customer_order_product FOREIGN KEY (product_id) REFERENCES product_catalog(id)
);

CREATE TABLE shipment_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    carrier VARCHAR(64) NULL,
    tracking_no VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    location VARCHAR(128) NULL,
    event_note VARCHAR(255) NOT NULL,
    event_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_shipment_event_order_time (order_id, event_time),
    CONSTRAINT fk_shipment_event_order FOREIGN KEY (order_id) REFERENCES customer_order(id)
);
