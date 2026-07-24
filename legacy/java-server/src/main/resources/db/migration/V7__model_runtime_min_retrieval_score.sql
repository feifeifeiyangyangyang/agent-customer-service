ALTER TABLE model_runtime_config
    ADD COLUMN min_retrieval_score DECIMAL(4,3) NOT NULL DEFAULT 0.350 AFTER top_k,
    ADD CONSTRAINT ck_model_runtime_min_retrieval_score CHECK (min_retrieval_score >= 0 AND min_retrieval_score <= 1);
