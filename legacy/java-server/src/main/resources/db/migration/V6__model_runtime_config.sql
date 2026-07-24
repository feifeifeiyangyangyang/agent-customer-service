CREATE TABLE model_runtime_config (
    id BIGINT PRIMARY KEY,
    temperature DECIMAL(3,2) NOT NULL,
    top_k INT NOT NULL,
    mock_enabled BOOLEAN NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT ck_model_runtime_temperature CHECK (temperature >= 0 AND temperature <= 1),
    CONSTRAINT ck_model_runtime_top_k CHECK (top_k >= 1 AND top_k <= 20)
);
